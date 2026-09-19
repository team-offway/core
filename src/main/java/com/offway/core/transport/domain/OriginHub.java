package com.offway.core.transport.domain;

import com.offway.core.common.geo.Coordinate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 출발지로 고를 수 있는 역·터미널 하나 — 제안 목록의 한 줄(#590).
 *
 * <p>사용자가 GPS 를 내주지 않는 대신 <b>출발지를 직접 고른다.</b> 이 객체가 그 선택지 하나다 —
 * 어떤 이름으로 보이고, 어떤 말로 찾히고, 어느 좌표를 뜻하는지를 함께 들고 있다.
 *
 * <p><b>좌표가 없는 허브는 만들 수 없다.</b> 좌표가 없으면 동선에 올릴 수 없어, 고를 수 있게 하면
 * 그 코스가 통째로 degrade 된다. 목록에서 빠지는 것이 낫다(터미널 789곳 중 226곳이 그렇다).
 *
 * @param code 앱이 그대로 되돌려 보내는 값 — 앱은 좌표를 다루지 않는다
 * @param type 역인가 터미널인가
 * @param rawName DB 의 원본 이름(업계 표기) — 검색에 쓰고 화면에는 안 쓴다
 * @param displayName 화면에 뜨는 이름 — 접미 규칙이나 별칭이 만든다
 * @param sido 속한 시도 — "서울" 같은 지역 검색이 이걸 본다
 * @param coordinate 이 허브의 좌표. 동선·최근접 계산이 쓰는 값
 * @param aliases 부르는 이름들. 별칭이 없는 허브는 빈 목록
 * @param curated 별칭이 붙은 허브인가 — 정렬에서 위로 올린다
 */
public record OriginHub(
        OriginCode code,
        OriginHubType type,
        String rawName,
        String displayName,
        OriginSido sido,
        Coordinate coordinate,
        List<SearchableName> aliases,
        boolean curated) {

    public OriginHub {
        Objects.requireNonNull(code, "출발지 코드는 필수입니다");
        Objects.requireNonNull(type, "허브 종류는 필수입니다");
        Objects.requireNonNull(rawName, "원본 이름은 필수입니다");
        Objects.requireNonNull(displayName, "표시 이름은 필수입니다");
        Objects.requireNonNull(sido, "시도는 필수입니다");
        Objects.requireNonNull(coordinate, "좌표는 필수입니다");
        Objects.requireNonNull(aliases, "별칭 목록은 필수입니다(없으면 빈 목록)");
    }

    /**
     * 검색어가 이 허브에 어떻게 걸렸는가 — 정렬 순서를 이 등급이 정한다.
     *
     * <p>등급을 나누는 이유: "서울" 은 서울의 허브 36곳 전부에 걸리는데, 그중 사용자가 찾는 것은
     * <b>이름에 그 말이 든 것</b>이다(서울역·동서울터미널·서울경부). 지역으로만 걸린 청량리·용산은
     * 그 아래다. 한 덩어리로 섞으면 36줄 중에 찾는 것이 어디 있는지 알 수 없다.
     */
    public enum Match {
        /**
         * 표시 이름이 검색어로 <b>시작한다</b>.
         *
         * <p>자동완성의 기본 규칙이다 — "서울" 을 친 사람이 첫 줄에서 기대하는 것은 서울역이고,
         * 이름 안쪽에 그 말이 든 동서울터미널이나 원본 이름으로 걸린 고속버스터미널은 그 뒤다.
         * 이 등급이 없으면 별칭이 붙은 허브가 늘 위로 올라가, 별칭을 더할 때마다 순서가 뒤집힌다.
         */
        NAME_PREFIX,
        /** 표시 이름 안쪽·원본 이름·별칭에 걸렸다. */
        NAME,
        /** 지역(시도)으로만 걸렸다. */
        AREA
    }

    /** 규칙(접미)이나 별칭으로 표시 이름을 정해 허브를 만든다. */
    public static OriginHub of(
            OriginHubType type, String hubCode, String rawName, OriginSido sido, Coordinate coordinate) {
        Objects.requireNonNull(type, "허브 종류는 필수입니다");
        Objects.requireNonNull(rawName, "원본 이름은 필수입니다");
        Optional<OriginHubAlias> alias = OriginHubAlias.find(type, rawName);
        return new OriginHub(
                OriginCode.ofHub(type, hubCode),
                type,
                rawName,
                alias.map(OriginHubAlias::displayName).orElseGet(() -> type.displayNameOf(rawName)),
                sido,
                coordinate,
                alias.map(OriginHubAlias::searchableAliases).orElseGet(List::of),
                alias.isPresent());
    }

    /**
     * 이 허브가 검색어에 걸리는가. 걸리면 어떤 등급으로 걸렸는지 함께 답한다.
     *
     * <p>이름 쪽을 먼저 본다 — 같은 허브가 이름과 지역 양쪽에 걸릴 때(서울의 "서울역") 더 높은
     * 등급으로 올려야 한다.
     */
    public Optional<Match> match(SearchableName query) {
        Objects.requireNonNull(query, "검색어는 필수입니다");
        if (SearchableName.of(displayName).value().startsWith(query.value())) {
            return Optional.of(Match.NAME_PREFIX);
        }
        if (matchesName(query)) {
            return Optional.of(Match.NAME);
        }
        if (sido.matches(query.value())) {
            return Optional.of(Match.AREA);
        }
        return Optional.empty();
    }

    private boolean matchesName(SearchableName query) {
        return SearchableName.of(displayName).contains(query)
                || SearchableName.of(rawName).contains(query)
                || aliases.stream().anyMatch(alias -> alias.contains(query));
    }

    /**
     * 같은 지점을 가리키는 허브를 하나로 접는 기준.
     *
     * <p><b>좌표가 기준인 이유.</b> TAGO 는 같은 터미널에 노선별로 코드를 따로 주고, 이름도 갈라 준다 —
     * 동서울이 5건(고속 4 · 시외 1), 서울 고속버스터미널 경부선 쪽이 {@code 서울경부} 와
     * {@code 서울고속버스터미널(경부)} 둘이다. 이름으로 접으면 후자가 안 접힌다.
     *
     * <p>좌표는 보정 마이그레이션이 같은 지점끼리 같은 값으로 맞춰 둬서(실측 2026-09-19: 소수점
     * 8자리까지 동일) 정확히 접힌다.
     *
     * <p><b>종류를 함께 본다.</b> 역과 터미널이 같은 좌표를 가질 수 있는데(역 앞 정류소) 그건 다른
     * 수단이라 접으면 안 된다.
     */
    public String dedupeKey() {
        return type.name() + '@' + coordinate.lat() + ',' + coordinate.lng();
    }

    /**
     * 접힌 둘 중 어느 쪽을 대표로 남길지 — 별칭이 붙은 쪽이 이긴다.
     *
     * <p>{@code 서울경부} 와 {@code 서울고속버스터미널(경부)} 가 같은 좌표인데, 남기는 쪽이 화면에
     * 뜬다. 별칭이 붙은 쪽을 남겨야 {@code 고속버스터미널(경부·영동)} 로 보인다.
     *
     * <p>둘 다 별칭이 없으면 <b>짧은 이름</b>을 남긴다 — 긴 이름은 노선·괄호가 붙은 변형이고, 짧은
     * 쪽이 그 터미널의 대표 표기다({@code 동서울} vs {@code 동서울터미널(직통)}). 길이가 같으면
     * 코드 순으로 정해 결과가 실행마다 흔들리지 않게 한다.
     */
    public OriginHub preferOver(OriginHub other) {
        if (curated != other.curated) {
            return curated ? this : other;
        }
        if (rawName.length() != other.rawName.length()) {
            return rawName.length() < other.rawName.length() ? this : other;
        }
        return code.value().compareTo(other.code.value()) <= 0 ? this : other;
    }
}
