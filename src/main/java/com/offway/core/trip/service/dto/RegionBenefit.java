package com.offway.core.trip.service.dto;

import com.offway.core.policy.domain.Policy;
import com.offway.core.policy.domain.PolicyType;
import java.util.Objects;
import lombok.Builder;

/**
 * 화면에 붙는 혜택 한 줄 — 홈 · 지역 상세 · 장소 상세가 <b>같은 값을 쓴다</b>(#413).
 *
 * <p>원래 {@code HomeResult} 안에 있었는데, 지역 상세와 장소 상세도 같은 값을 쓰면서 홈의 결과 타입
 * 안쪽을 가리키게 됐다. 세 화면이 공유하는 값이므로 밖으로 뺀다.
 *
 * <p><b>신청 주소를 함께 든다.</b> 이 값이 없으면 앱은 링크를 얻으려고 {@code policyId} 로 정책 상세를
 * 한 번 더 불러야 한다 — 카드 하나 그리는 데 왕복이 하나 는다. 장소 상세는 그마저도 못 했다(문구만
 * 내려가 {@code policyId} 자체가 없었다).
 *
 * @param policyId 눌렀을 때 갈 혜택 상세
 * @param type 정책 분류 — 뱃지 문구의 주인이다
 * @param text 뱃지 문구
 * @param applyUrl 신청 페이지. <b>없을 수 있다</b> — 아직 주소를 안 적은 정책이면 앱이 링크만 접는다
 */
@Builder
public record RegionBenefit(long policyId, PolicyType type, String text, String applyUrl) {

    /**
     * 정책에서 화면이 쓸 조각만 뽑는다.
     *
     * <p><b>저장된 정책만 넘어온다.</b> {@code policyId} 는 앱이 혜택 상세로 갈 때 쓰는 값이라 없으면
     * 뜻이 없다. 실제로 이 자리에는 리포지토리가 찾아온 정책만 오지만, 아직 저장 안 된 것이 오면
     * 언박싱 NPE 가 나 원인이 안 보인다 — 이름을 붙여 끊는다.
     */
    public static RegionBenefit from(Policy policy) {
        return RegionBenefit.builder()
                .policyId(Objects.requireNonNull(policy.getId(), "저장된 정책이어야 합니다 — id 가 없습니다"))
                .type(policy.getType())
                .text(policy.badgeText())
                .applyUrl(policy.getApplyUrl())
                .build();
    }
}
