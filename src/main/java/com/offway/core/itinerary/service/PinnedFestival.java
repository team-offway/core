package com.offway.core.itinerary.service;

import com.offway.core.trip.domain.TravelWindow;
import com.offway.core.trip.service.dto.PoiCandidate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * 축제를 <b>열리는 날</b>에 못 박는다(#616).
 *
 * <h2>왜 따로 필요한가</h2>
 *
 * <p>후보 중 축제만 날짜 제약이 있다. 관광지는 아무 날에나 놓아도 되지만 축제는 그 며칠에만 열린다.
 *
 * <p>그런데 날 배정은 <b>동선 순서</b>가 정한다 — 기준점 최근접으로 정렬한 뒤 하루씩 잘라 담는다. 축제가
 * 그 줄의 어디에 놓이느냐는 좌표가 정하므로, <b>2박3일 중 첫날만 하는 축제가 셋째 날 칸에 들어간다.</b>
 * 그러면 사용자가 문 닫힌 곳에 간다.
 *
 * <p>후보를 여행 구간으로 거르는 것(#616 의 앞쪽)만으로는 이 문제가 안 풀린다. "여행 중 하루라도 열린다"
 * 와 "이 날에 열린다" 는 다른 질문이고, 거르는 쪽은 앞의 것만 답한다.
 *
 * <h2>어느 날에 놓나</h2>
 *
 * <p><b>축제가 열리는 첫 날</b>이다. 여러 날 겹치면 앞쪽을 쓴다 — 뒤로 밀면 마지막 날에 놓일 수 있고,
 * 마지막 날은 돌아가는 날이라 일정이 짧다(첫날·마지막날은 이동이 칸을 먹는다).
 *
 * <h2>왜 값객체인가</h2>
 *
 * <p>"못 박을 축제가 있나 / 어느 날인가 / 그 날에 어떻게 꽂나" 가 한 덩이다. 서비스 안에 풀어 두면
 * {@code null} 검사와 날 비교가 루프 안에 흩어지고, 한 군데만 빠뜨려도 축제가 두 날에 들어가거나 아무
 * 날에도 안 들어간다.
 */
record PinnedFestival(PoiCandidate festival, int dayNumber) {

    /** 못 박을 것이 없음 — 축제가 없거나, 기간을 모르거나, 여행일을 모르는 경우. */
    private static final PinnedFestival NONE = new PinnedFestival(null, 0);

    /** 여행 첫날 — 볼거리 칸이 줄어들 수 있는 유일한 날이다(나머지는 {@code DayStart.fullDay()}). */
    private static final int FIRST_DAY = 1;

    /**
     * 고른 볼거리 중 <b>기간을 아는 축제</b>를 찾아 놓을 날을 정한다.
     *
     * <p>기간을 모르는 축제는 못 박지 않는다 — 어느 날에 열리는지 모르므로 날짜 제약이 없는 셈이고,
     * 일반 후보로 두면 동선이 알맞은 자리에 놓는다. "모른다" 를 "안 한다" 로 바꾸지 않는다.
     *
     * <p>축제가 둘 이상 실릴 일은 없다({@code FESTIVAL_SLOTS} 가 1). 그래도 첫 번째만 쓰는 이유는
     * 상한이 늘었을 때 <b>조용히 두 개를 못 박는 것보다 하나만 박는 것이 안전</b>하기 때문이다 —
     * 나머지는 일반 후보로 남아 동선이 배치한다.
     */
    static PinnedFestival of(List<PoiCandidate> sights, TravelWindow window, int firstDayCapacity) {
        if (window == null) {
            return NONE;
        }
        return sights.stream()
                .filter(PoiCandidate::isFestival)
                .filter(PoiCandidate::hasKnownPeriod)
                .findFirst()
                .map(festival ->
                        new PinnedFestival(festival, firstOpenDay(festival, window, firstDayCapacity)))
                .filter(PinnedFestival::exists)
                .orElse(NONE);
    }

    /**
     * 축제가 열리는 첫 여행일(1부터). 놓을 날이 없으면 0.
     *
     * <p>0 이 나오는 것은 후보 필터와 어긋난 경우다 — 구간이 겹쳐 후보로 올라왔으면 열리는 날이 하나는
     * 있어야 한다. 그때는 못 박지 않고 일반 후보로 두어, 어긋남이 코스를 비우는 대신 조용히 동선에
     * 맡겨지게 한다.
     *
     * <h2>칸이 없는 첫날은 건너뛴다</h2>
     *
     * <p>늦게 도착하면 첫날 볼거리 칸이 <b>0</b> 이 될 수 있다({@code DayStart.sightCapacity} 가
     * 오전·오후 몫의 합이라, 둘 다 지났으면 0 이다). 그 날에 축제를 못 박으면 <b>이미 지난 시간대에
     * 슬롯</b>이 생긴다 — 갈 수 없는 시간에 일정을 받는 것이다.
     *
     * <p>그렇다고 축제를 버리지는 않는다. 축제가 이튿날에도 열린다면 그 날에 놓는다 — 첫날만 칸이
     * 줄어들고 나머지 날은 {@code DayStart.fullDay()} 라 칸이 있다. 그래서 첫날만 확인하면 된다.
     *
     * @param firstDayCapacity 1일차 볼거리 칸 수. 0 이면 1일차를 건너뛴다
     */
    private static int firstOpenDay(PoiCandidate festival, TravelWindow window, int firstDayCapacity) {
        for (int day = 1; day <= window.days(); day++) {
            if (day == FIRST_DAY && firstDayCapacity <= 0) {
                continue;
            }
            LocalDate date = window.dateOf(day);
            if (festival.isOpenOn(date)) {
                return day;
            }
        }
        return 0;
    }

    private boolean exists() {
        return festival != null && dayNumber > 0;
    }

    /** 못 박은 축제를 일반 후보 줄에서 뺀다 — 안 빼면 엉뚱한 날이 먼저 집어 간다. */
    void removeFrom(List<PoiCandidate> remaining) {
        if (exists()) {
            remaining.remove(festival);
        }
    }

    /**
     * 이 날의 볼거리를 채운다.
     *
     * <p>축제를 놓는 날이면 <b>한 칸을 축제에 주고 남은 칸만</b> 일반 후보로 채운다. 축제가 덧붙는 것이
     * 아니라 그날 볼거리 한 칸을 쓰는 것이므로, 하루 볼거리 수가 늘지 않는다.
     *
     * <p>축제를 앞에 둔다. 그 뒤 자차면 실도로 기준으로 다시 정렬되므로 <b>하루 안의 순서는 동선이
     * 정한다</b> — 여기서 앞에 두는 것은 "이 날에 반드시 들어간다" 는 뜻일 뿐이다.
     *
     * @param dayNumber 몇 일차인가(1부터)
     * @param remaining 아직 안 쓴 일반 후보. 쓴 만큼 이 목록에서 빠진다
     * @param capacity 이 날 볼거리 칸 수
     * @param take 일반 후보를 capacity 만큼 집어 가는 규칙(분류 상한을 지키는 그 규칙)
     */
    List<PoiCandidate> fill(int dayNumber, List<PoiCandidate> remaining, int capacity,
            BiFunction<List<PoiCandidate>, Integer, List<PoiCandidate>> take) {
        if (!exists() || dayNumber != this.dayNumber) {
            return take.apply(remaining, capacity);
        }
        if (capacity <= 0) {
            // **쓸 수 있는 시간대가 없는 날이다.** 억지로 넣으면 이미 지난 시간대에 슬롯이 생긴다 —
            // 갈 수 없는 시간에 일정을 받는 것이다. 빈 날은 코스에서 통째로 빠지는 것이 정상이다.
            //
            // 여기 닿는 것은 위 firstOpenDay 가 칸 없는 첫날을 건너뛰지 못한 경우뿐이다(축제가 첫날에만
            // 열리는 경우). 그때는 축제를 잃지만, 갈 수 없는 일정을 주는 것보다 낫다.
            return take.apply(remaining, capacity);
        }
        if (capacity == 1) {
            // 칸이 하나뿐인 날이면 축제만 놓는다. 첫날이 늦게 시작하는 경우가 이렇다.
            return List.of(festival);
        }
        List<PoiCandidate> filled = new ArrayList<>();
        filled.add(festival);
        filled.addAll(take.apply(remaining, capacity - 1));
        return List.copyOf(filled);
    }
}
