package com.offway.core.transport.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 규칙으로 안 되는 허브의 표시 이름과 검색어(#590).
 *
 * <p><b>왜 목록이 짧은가.</b> 표시 이름은 {@link OriginHubType} 의 접미 규칙이, 구두점 차이는
 * {@link SearchableName} 의 정규화가 이미 흡수한다. 그래서 여기 남는 것은 <b>원본 이름과 부르는
 * 이름이 진짜로 다른</b> 경우뿐이다.
 *
 * <p>지금은 서울 고속버스터미널 하나다. TAGO 는 그 자리를 노선으로 갈라 {@code 서울경부}·
 * {@code 센트럴시티(서울)} 로 주는데, 사용자는 둘 다 "고속버스터미널" 이라 부른다. 규칙으로는
 * {@code 서울경부터미널} 이 되어 검색도 표시도 어긋난다.
 *
 * <p><b>표시 이름에 노선을 남긴다.</b> 두 곳을 모두 {@code 고속버스터미널} 로만 쓰면 목록에 같은 줄이
 * 둘 뜬다 — 실제로는 붙어 있는 다른 건물이고 가는 노선이 다르다(경부·영동 / 호남·전라). 그래서
 * 괄호로 노선을 남겨 고르는 사람이 구별할 수 있게 한다.
 *
 * <p><b>늘리는 기준</b> — 사용자가 실제로 다르게 부르는 이름이 확인됐을 때만 더한다. 추측으로 채우면
 * 없는 별칭이 검색에 걸려 엉뚱한 허브가 상위에 뜬다.
 */
public enum OriginHubAlias {

    /** 서울 고속버스터미널 경부·영동선 쪽(반포). */
    SEOUL_EXPRESS_GYEONGBU(
            OriginHubType.BUS_TERMINAL, "서울경부", "고속버스터미널(경부·영동)",
            List.of("고속버스터미널", "고속터미널", "강남터미널", "반포터미널")),

    /** 같은 자리 호남·전라선 쪽(센트럴시티). */
    SEOUL_EXPRESS_HONAM(
            OriginHubType.BUS_TERMINAL, "센트럴시티(서울)", "고속버스터미널(호남·전라)",
            List.of("고속버스터미널", "고속터미널", "강남터미널", "센트럴시티"));

    private final OriginHubType type;

    /** DB 에 있는 원본 이름 — 이 값으로 찾는다. */
    private final String rawName;

    private final String displayName;

    private final List<String> aliases;

    OriginHubAlias(OriginHubType type, String rawName, String displayName, List<String> aliases) {
        this.type = type;
        this.rawName = rawName;
        this.displayName = displayName;
        this.aliases = aliases;
    }

    public String displayName() {
        return displayName;
    }

    /** 검색에 쓸 별칭들 — 정규화해 둔 형태로 준다. */
    public List<SearchableName> searchableAliases() {
        return aliases.stream().map(SearchableName::of).toList();
    }

    /** 이 종류·원본 이름에 붙은 별칭이 있는가. 없으면 규칙(접미)을 쓴다. */
    public static Optional<OriginHubAlias> find(OriginHubType type, String rawName) {
        return Arrays.stream(values())
                .filter(alias -> alias.type == type && alias.rawName.equals(rawName))
                .findFirst();
    }
}
