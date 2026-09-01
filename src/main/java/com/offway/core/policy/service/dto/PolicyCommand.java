package com.offway.core.policy.service.dto;

import com.offway.core.policy.domain.Policy;
import com.offway.core.policy.domain.PolicyType;
import java.time.LocalDate;
import lombok.Builder;

/**
 * 정책 생성·수정 커맨드(#344) — 백오피스 내부용.
 *
 * <p><b>생성과 수정이 같은 모양이다.</b> 수정이 부분 갱신이 아니라 전체 교체이기 때문이고, 그 이유는
 * {@link Policy#update} 주석에 있다.
 *
 * <p>{@code updatedBy} 는 여기 없다. 그 값은 요청이 아니라 <b>토큰이 정한다</b> — 커맨드에 넣으면 어드민이
 * 남의 이름으로 흔적을 남길 수 있다.
 *
 * @param type 7대 혜택 분류. 뱃지 문구와 대상 지역이 여기 묶여 있다
 * @param name 정책명
 * @param benefitDetail 혜택 상세. 사용자에게 그대로 나간다
 * @param targetAudience 대상. 사용자에게 그대로 나간다
 * @param periodStart 노출 시작일. 비우면 "이미 시작했다"
 * @param periodEnd 노출 종료일. 비우면 "끝나지 않는다"
 * @param periodNote 날짜 둘로 다 말할 수 없을 때의 보충 문구
 * @param applyUrl 신청 페이지. {@code https} 만 받는다
 * @param verified 사실 확인이 끝났는가. <b>거짓이면 앱에 안 나간다</b>
 * @param checkedOn 사람이 마지막으로 출처를 확인한 날. 낡음 알림이 읽는 값이다
 */
@Builder
public record PolicyCommand(
        PolicyType type,
        String name,
        String benefitDetail,
        String targetAudience,
        LocalDate periodStart,
        LocalDate periodEnd,
        String periodNote,
        String applyUrl,
        boolean verified,
        LocalDate checkedOn) {

    /** 새 정책. 검증은 도메인이 한다 — 여기서 미리 걸러 두면 규칙이 두 곳이 된다. */
    public Policy toPolicy(String updatedBy) {
        return Policy.builder()
                .type(type)
                .name(name)
                .benefitDetail(benefitDetail)
                .targetAudience(targetAudience)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .periodNote(periodNote)
                .applyUrl(applyUrl)
                .verified(verified)
                .checkedOn(checkedOn)
                .updatedBy(updatedBy)
                .build();
    }

    /** 기존 정책을 이 값으로 갈아 끼운다. */
    public void applyTo(Policy policy, String updatedBy) {
        policy.update(type, name, benefitDetail, targetAudience, periodStart, periodEnd,
                periodNote, applyUrl, verified, checkedOn, updatedBy);
    }
}
