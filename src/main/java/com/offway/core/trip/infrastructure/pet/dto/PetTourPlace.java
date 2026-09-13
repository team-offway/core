package com.offway.core.trip.infrastructure.pet.dto;

/**
 * 반려동반 목록의 한 건(#566) — 전국 조회가 주는 값 중 <b>우리가 쓰는 것만</b>.
 *
 * <p>좌표·사진·분류도 함께 오지만 담지 않는다. 이 표는 새 후보를 데려오는 것이 아니라 이미 풀에 있는
 * 장소에 표식을 붙이는 것이라, 매칭 키와 지역만 있으면 된다({@code PetFriendlyPlace} 참고).
 *
 * @param contentId TourAPI 콘텐츠 ID — 장소 풀 매칭 키
 * @param title 장소명
 * @param legalCode 법정 시군구코드 5자리 — {@code lDongRegnCd} + {@code lDongSignguCd}. 못 만들면 null
 */
public record PetTourPlace(String contentId, String title, String legalCode) {

    /**
     * 우리 89곳과 맞춰볼 수 있는가.
     *
     * <p>실측에서 9,679건 중 4건이 법정동 코드가 비어 있었다. 그건 어느 지역인지 모르는 것이라
     * 지역별 표에 담을 수 없다 — <b>추측해서 어딘가에 넣지 않는다.</b>
     */
    public boolean isMatchable() {
        return contentId != null && title != null && legalCode != null;
    }
}
