package com.offway.core.trip.domain;

import com.offway.core.common.geo.Coordinate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * 지역에서 <b>많이 찾는 순서</b>(#527) — 중심관광지 순위를 후보에 맞춰 준다.
 *
 * <h2>왜 매칭이 필요한가</h2>
 *
 * <p>중심관광지({@code hub_attraction})는 이름과 좌표만 들고 있고 우리 후보의 식별자를 모른다. 연관
 * 관광지가 적재 시점에 인허가 장소로 이어 붙는 것과 달리, 이쪽은 이어 붙일 곳이 없다 — 볼거리 후보의
 * 대부분이 TourAPI 콘텐츠라 식별자 공간 자체가 다르다.
 *
 * <h2>좁게 맞춘다</h2>
 *
 * <p>이름을 정규화해 같거나, {@value #MATCH_KM}㎞ 안이면 같은 곳으로 본다. 실측(2026-09-08, 4개 지역)
 * 에서 중심관광지의 32~62%가 붙고 볼거리 풀의 17~26%가 순위를 얻는다.
 *
 * <p><b>반경을 넓히지 않는다.</b> 2㎞ 로 늘리면 매칭률이 65~90%까지 오르지만 그건 매칭이 아니라
 * 추측이다 — 군 단위에서 2㎞ 안에는 전혀 다른 곳이 여럿 있다. 엉뚱한 곳에 인기를 붙이면 그 코스는
 * <b>조용히 틀린다</b>. 못 맞춘 것은 순위를 안 주고, 호출자가 좌표 군집으로 채운다.
 *
 * <h2>같은 곳이 여럿에 걸리면</h2>
 *
 * <p>가장 높은 순위(작은 값)를 준다. 이름으로도 좌표로도 걸릴 수 있어 중복이 정상이고, 그때 더 낮은
 * 순위를 주면 인기 있는 곳이 뒤로 밀린다.
 */
public final class Popularity {

    /** 같은 곳으로 볼 거리(㎞). 넓히면 매칭이 아니라 추측이 된다 — 위 설명 참고. */
    private static final double MATCH_KM = 0.3;

    /** 이름 비교 전에 지울 것 — 공백·괄호·가운뎃점처럼 표기만 다른 자리. */
    private static final Pattern NOT_NAME = Pattern.compile("[^0-9a-z가-힣]");

    private static final Popularity NONE = new Popularity(List.of());

    private final List<Entry> entries;

    private Popularity(List<Entry> entries) {
        this.entries = entries;
    }

    /** 중심관광지가 없는 지역 — 아무 후보에도 순위를 주지 않는다. */
    public static Popularity none() {
        return NONE;
    }

    /**
     * 중심관광지 목록으로 만든다.
     *
     * <p><b>좌표가 없어도 버리지 않는다.</b> 규칙은 "이름이 같거나 가깝거나" 인데, 좌표가 없다고 통째로
     * 빼면 <b>이름 일치까지 같이 막혀</b> 규칙이 조용히 "좌표 필수" 로 좁아진다. 좌표 없는 항목은 이름으로만
     * 견주고, 거리 비교에서만 빠진다.
     *
     * <p>이름도 좌표도 없는 항목만 버린다 — 어느 쪽으로도 견줄 수가 없다.
     *
     * <p>지금 운영에는 좌표 없는 중심관광지가 <b>한 건도 없다</b>(2,669건 전수). 그래도 엔티티가 좌표를
     * 강제하지 않고 적재도 거르지 않으므로, 언젠가 들어와도 규칙이 어긋나지 않게 둔다.
     */
    public static Popularity of(List<HubAttraction> hubs) {
        if (hubs == null || hubs.isEmpty()) {
            return NONE;
        }
        List<Entry> built = new ArrayList<>(hubs.size());
        for (HubAttraction hub : hubs) {
            String name = normalize(hub.getName());
            Coordinate at = hub.getLat() == null || hub.getLng() == null
                    ? null : new Coordinate(hub.getLat(), hub.getLng());
            if (name.isEmpty() && at == null) {
                continue;
            }
            built.add(new Entry(name, at, hub.getHubRank()));
        }
        return built.isEmpty() ? NONE : new Popularity(List.copyOf(built));
    }

    /** 아무 후보에도 순위를 못 주는가 — 호출자가 곧장 좌표 군집으로 가게 한다. */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * 이 장소의 인기 순위(1위가 가장 높다). 못 맞추면 비어 있다.
     */
    public OptionalInt rankOf(String title, double lat, double lng) {
        if (entries.isEmpty()) {
            return OptionalInt.empty();
        }
        String name = normalize(title);
        Coordinate at = new Coordinate(lat, lng);
        int best = Integer.MAX_VALUE;
        for (Entry entry : entries) {
            if (!entry.matches(name, at)) {
                continue;
            }
            best = Math.min(best, entry.rank());
        }
        return best == Integer.MAX_VALUE ? OptionalInt.empty() : OptionalInt.of(best);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return NOT_NAME.matcher(value.toLowerCase(Locale.KOREAN)).replaceAll("");
    }

    /** @param coordinate 없을 수 있다 — 그때는 이름으로만 견준다. */
    private record Entry(String name, Coordinate coordinate, int rank) {

        boolean matches(String otherName, Coordinate other) {
            // 빈 이름끼리 같다고 보면 좌표가 먼 곳까지 통째로 묶인다.
            if (!name.isEmpty() && name.equals(otherName)) {
                return true;
            }
            return coordinate != null && coordinate.haversineKmTo(other) <= MATCH_KM;
        }
    }
}
