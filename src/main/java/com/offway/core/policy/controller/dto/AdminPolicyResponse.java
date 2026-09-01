package com.offway.core.policy.controller.dto;

import com.offway.core.policy.domain.Policy;
import com.offway.core.policy.domain.PolicyType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

/**
 * 백오피스가 보는 정책(#344) — <b>앱이 보는 것보다 넓다</b>.
 *
 * <p>{@code PolicyResponse} 는 앱이 그릴 것만 담는다. 여기는 <b>고칠 수 있어야</b> 하므로 검증 여부·
 * 확인일자·감사 흔적까지 전부 내린다. 두 응답을 하나로 합치면 앱 응답에 미검증 여부가 딸려 나가고,
 * 그건 팀 밖에 알릴 이유가 없는 값이다.
 *
 * @param badgeText 분류가 소유하는 뱃지 문구 — <b>고칠 수 없는 값</b>이다. 분류를 바꾸면 함께 바뀐다는
 *     것을 화면이 보여주려고 함께 내린다
 * @param updatedBy 마지막으로 고친 어드민. <b>seed 로 들어온 행은 null</b> 이다 — 사람이 손댄 적이 없다
 */
@Builder
public record AdminPolicyResponse(
        @Schema(example = "2") long id,
        @Schema(example = "STAY_FESTA") PolicyType type,
        @Schema(example = "숙박 할인") String badgeText,
        @Schema(example = "2026 대한민국 숙박세일 페스타") String name,
        @Schema(nullable = true) String benefitDetail,
        @Schema(nullable = true) String targetAudience,
        @Schema(example = "2026-06-11", nullable = true) LocalDate periodStart,
        @Schema(example = "2026-08-31", nullable = true) LocalDate periodEnd,
        @Schema(nullable = true) String periodNote,
        @Schema(example = "https://ktostay.visitkorea.or.kr", nullable = true) String applyUrl,
        @Schema(example = "true") boolean verified,
        @Schema(example = "2026-08-28", nullable = true) LocalDate checkedOn,
        @Schema(example = "박세빈", nullable = true) String updatedBy) {

    /**
     * 빌더로 조립하는 이유 — 문자열 필드가 다섯이고 {@code LocalDate} 가 셋이라, 위치 인수면 순서가
     * 뒤바뀌어도 컴파일이 통과한다. 특히 <b>시작일과 종료일이 붙어 있어</b> 뒤집히면 뱃지가 영영 안 뜬다.
     */
    public static AdminPolicyResponse from(Policy policy) {
        return AdminPolicyResponse.builder()
                .id(policy.getId())
                .type(policy.getType())
                .badgeText(policy.badgeText())
                .name(policy.getName())
                .benefitDetail(policy.getBenefitDetail())
                .targetAudience(policy.getTargetAudience())
                .periodStart(policy.getPeriodStart())
                .periodEnd(policy.getPeriodEnd())
                .periodNote(policy.getPeriodNote())
                .applyUrl(policy.getApplyUrl())
                .verified(policy.isVerified())
                .checkedOn(policy.getCheckedOn())
                .updatedBy(policy.getUpdatedBy())
                .build();
    }

    public static List<AdminPolicyResponse> from(List<Policy> policies) {
        return policies.stream().map(AdminPolicyResponse::from).toList();
    }
}
