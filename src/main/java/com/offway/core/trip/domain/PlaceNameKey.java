package com.offway.core.trip.domain;

import java.util.Objects;

/**
 * 서로 다른 출처의 장소 이름을 맞대 보기 위한 열쇠(#186).
 *
 * <h2>왜 필요한가</h2>
 *
 * <p>연관 관광지({@code TarRlteTarService1})는 <b>좌표를 주지 않는다.</b> 응답 필드에 {@code mapX}·
 * {@code mapY} 가 없다 — 중심관광지 API 는 주는데 이쪽은 없다. 좌표가 없으면 이동시간도 지도 핀도
 * 슬롯 배치도 못 한다.
 *
 * <p>그래서 <b>인허가 장소(#144)와 이름으로 잇는다.</b> 그런데 같은 가게를 두 출처가 다르게 적는다.
 *
 * <pre>
 *   동해원/[중식]      → 동해원
 *   유천냉면/공주점     → 유천냉면
 *   카페 마암          → 카페마암
 * </pre>
 *
 * <h2>어디까지 지우나</h2>
 *
 * <p><b>슬래시 뒤를 버린다.</b> 연관 관광지가 분류·지점을 슬래시로 덧붙이는데, 인허가 상호에는 그게
 * 없다. 실측(공주시)에서 이 규칙 하나가 매칭률을 크게 올렸다.
 *
 * <p><b>괄호 안을 버리고 공백을 지운다.</b> 표기 차이일 뿐 다른 가게가 아니다.
 *
 * <p><b>그 이상은 안 건드린다.</b> 지점명을 더 떼면 "○○식당 본점" 과 "○○식당 2호점" 이 한 곳으로
 * 접혀, 실제로 다른 가게에 남의 좌표가 붙는다. 못 맞추는 것보다 <b>틀리게 맞추는 쪽이 나쁘다</b> —
 * 전자는 후보에서 빠지고 끝이지만 후자는 코스가 엉뚱한 데를 지난다.
 */
public record PlaceNameKey(String value) {

    /** 슬래시부터 끝까지 — 분류(`/[중식]`)·지점(`/공주점`)이 붙는 자리다. */
    private static final String SLASH_TAIL = "/.*$";

    /** 괄호와 그 안 — `(주)`·`(본점)` 처럼 표기 차이만 담는다. */
    private static final String BRACKETS = "[\\(\\[][^\\)\\]]*[\\)\\]]";

    private static final String WHITESPACE = "\\s+";

    public PlaceNameKey {
        Objects.requireNonNull(value, "정규화된 이름은 null 일 수 없습니다.");
    }

    /**
     * 이름을 맞대 볼 열쇠로 만든다.
     *
     * @return 비교할 수 없는 이름(null·빈 문자열·지우고 나니 빈 것)이면 빈 값
     */
    public static java.util.Optional<PlaceNameKey> of(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return java.util.Optional.empty();
        }
        String normalized = rawName
                .replaceAll(SLASH_TAIL, "")
                .replaceAll(BRACKETS, "")
                .replaceAll(WHITESPACE, "")
                .toLowerCase();
        return normalized.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(new PlaceNameKey(normalized));
    }
}
