package com.offway.core.trip.infrastructure.datalab.dto;

/**
 * 연관 관광지 한 건(TarRlteTarService1 응답).
 *
 * <p><b>좌표가 없다.</b> 응답 필드에 {@code mapX}·{@code mapY} 가 아예 없어서, 인허가 장소와 이름으로
 * 이어 붙여야 우리 코스에 쓸 수 있다(#186).
 *
 * @param hubCode 중심 관광지 코드({@code tAtsCd}) — 이 줄이 "무엇과 함께 가는가" 의 그 무엇
 * @param hubName 중심 관광지명({@code tAtsNm})
 * @param code 연관 관광지 코드({@code rlteTatsCd})
 * @param name 연관 관광지명({@code rlteTatsNm}) — {@code 동해원/[중식]} 처럼 분류가 붙어 온다
 * @param rank 중심 관광지 안에서의 순위({@code rlteRank}, 1부터)
 * @param categoryLarge 대분류({@code rlteCtgryLclsNm}) — 관광지·음식·숙박
 * @param categoryMedium 중분류({@code rlteCtgryMclsNm})
 * @param sigunguName 연관 장소가 속한 시군구({@code rlteSignguNm}) — <b>지역 밖을 거르는 근거</b>
 */
public record RelatedAttractionItem(
        String hubCode,
        String hubName,
        String code,
        String name,
        int rank,
        String categoryLarge,
        String categoryMedium,
        String sigunguName) {

    /**
     * 엔티티로 만들 수 있는가 — 좌표를 뺀 필수값이 다 있는지 본다.
     *
     * <p>어댑터가 이걸로 걸러야 하는 이유는 {@code HubAttractionItem} 과 같다. 통과시키면 나중에 예외가
     * 터지는데, 그 자리는 호출자가 외부 실패로 잡는 경계 밖이라 한 건이 배치 전체를 멈춘다.
     */
    public boolean isComplete() {
        return rank >= 1
                && hasText(hubCode) && hasText(hubName)
                && hasText(code) && hasText(name)
                && hasText(categoryLarge);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
