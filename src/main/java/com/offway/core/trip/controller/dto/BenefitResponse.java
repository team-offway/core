package com.offway.core.trip.controller.dto;

import com.offway.core.policy.domain.PolicyType;
import com.offway.core.trip.service.dto.RegionBenefit;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 화면에 붙는 혜택 뱃지 — 홈 · 지역 상세 · 장소 상세가 <b>같은 모양</b>으로 받는다(#413).
 *
 * <p>세 화면이 각자 같은 record 를 들고 있었다. 홈과 지역 상세는 "필드 이름·순서를 같게 둔다" 는 주석으로
 * 그 약속을 지켰고, 장소 상세만 <b>문구 하나짜리 문자열</b>이라 눌러도 갈 곳이 없었다. 셋을 하나로 모은다 —
 * 같은 값이 화면마다 다른 모양으로 오면 앱이 화면마다 다른 파서를 든다.
 *
 * @param text 뱃지 문구. 문구의 주인은 {@link PolicyType} 이라 같은 분류면 글자도 같다
 * @param policyType 분류
 * @param policyId 누르면 이 혜택의 상세로 간다
 * @param applyUrl 신청 페이지. <b>없으면 null</b> — 앱이 링크만 접는다. 아직 주소를 안 적은 정책이 있다
 */
public record BenefitResponse(
        @Schema(example = "숙박 할인") String text,
        @Schema(example = "STAY_FESTA") PolicyType policyType,
        @Schema(example = "2") long policyId,
        @Schema(example = "https://ktostay.visitkorea.or.kr", nullable = true) String applyUrl) {

    /** 없는 혜택은 그대로 없다 — 빈 객체를 만들면 앱이 뱃지 자리를 그린다. */
    public static BenefitResponse from(RegionBenefit benefit) {
        if (benefit == null) {
            return null;
        }
        return new BenefitResponse(benefit.text(), benefit.type(), benefit.policyId(), benefit.applyUrl());
    }
}
