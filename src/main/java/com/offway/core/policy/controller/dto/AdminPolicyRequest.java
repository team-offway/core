package com.offway.core.policy.controller.dto;

import com.offway.core.policy.domain.PolicyType;
import com.offway.core.policy.service.dto.PolicyCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 정책 생성·수정 요청(#344) — 생성과 수정이 <b>같은 모양</b>이다.
 *
 * <p>수정이 부분 갱신이 아니라 전체 교체이기 때문이다. 화면도 폼 전체를 들고 있으므로 전부 받는 편이
 * 자연스럽다.
 *
 * <h2>여기서 검증하는 것과 안 하는 것</h2>
 *
 * <p>여기는 <b>모양</b>만 본다 — 비었는지, 길이가 컬럼을 넘는지. {@code https} 스킴·기간 순서처럼
 * <b>값들 사이의 관계</b>는 도메인이 본다. 뱃지 중복은 다른 행까지 봐야 알 수 있어 서비스가 본다.
 *
 * <h2>선택 필드는 primitive 를 쓰지 않는다</h2>
 *
 * <p>이 스택은 Jackson 3 이고 {@code FAIL_ON_NULL_FOR_PRIMITIVES} 가 켜져 있다. {@code boolean} 으로
 * 두면 JSON 에 그 필드가 <b>없을 때</b> 매핑이 깨져 요청 전체가 400 이 된다 — 필드 하나를 생략한 것이
 * 값 오류로 보고되는 셈이라 어드민은 어디가 틀렸는지 알 수 없다.
 *
 * @param type 7대 혜택 분류. <b>자유 입력이 아니다</b> — 뱃지 문구와 대상 지역이 여기 묶여 있다
 * @param name 정책명
 * @param benefitDetail 혜택 상세. 사용자에게 그대로 나간다
 * @param targetAudience 대상. 사용자에게 그대로 나간다
 * @param periodStart 노출 시작일. 비우면 "이미 시작했다" 로 읽는다
 * @param periodEnd 노출 종료일. <b>비우면 끝나지 않는다</b> — 사업이 끝나도 뱃지가 남으므로 바깥 경계를 넣는다
 * @param periodNote 지자체별로 기간이 다를 때의 보충 문구
 * @param applyUrl 신청 페이지. {@code https} 만 받는다(도메인 검증 → 400 {@code POLICY-002})
 * @param verified 사실 확인이 끝났는가. 안 보내면 <b>거짓</b> — 확인 전 정책이 앱에 나가면 안 된다
 * @param checkedOn 출처를 마지막으로 확인한 날. 낡음 알림이 읽는 값이라 고칠 때 함께 갱신한다
 */
public record AdminPolicyRequest(
        @Schema(example = "STAY_FESTA", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull PolicyType type,
        @Schema(example = "2026 대한민국 숙박세일 페스타", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank @Size(max = 100) String name,
        @Schema(example = "숙박 할인권 · 1박 7만원 미만 2만원", nullable = true) @Size(max = 500) String benefitDetail,
        @Schema(example = "전 국민(참여 온라인여행사에서 발급)", nullable = true) @Size(max = 200) String targetAudience,
        @Schema(example = "2026-06-11", nullable = true) LocalDate periodStart,
        @Schema(example = "2026-08-31", nullable = true) LocalDate periodEnd,
        @Schema(example = "매일 오전 10시 선착순 발급", nullable = true) @Size(max = 200) String periodNote,
        @Schema(example = "https://ktostay.visitkorea.or.kr", nullable = true) @Size(max = 500) String applyUrl,
        @Schema(example = "true", nullable = true) Boolean verified,
        @Schema(example = "2026-08-28", nullable = true) LocalDate checkedOn) {

    /** <b>안 보내면 안 내린다.</b> 확인이 안 끝난 정책이 곧바로 사용자에게 보이면 안 된다. */
    private static final boolean DEFAULT_VERIFIED = false;

    public PolicyCommand toCommand() {
        return PolicyCommand.builder()
                .type(type)
                .name(name)
                .benefitDetail(benefitDetail)
                .targetAudience(targetAudience)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .periodNote(periodNote)
                .applyUrl(applyUrl)
                .verified(verified != null ? verified : DEFAULT_VERIFIED)
                .checkedOn(checkedOn)
                .build();
    }
}
