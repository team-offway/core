package com.offway.core.itinerary.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.offway.core.policy.domain.BenefitScope;
import com.offway.core.policy.domain.PolicyType;
import org.junit.jupiter.api.Test;

/**
 * 혜택이 코스의 어느 자리에 붙는가(#140) — 정책의 대상 분류를 슬롯 종류로 옮기는 대응.
 *
 * <p>정책 도메인은 슬롯을 모른다. 대응이 여기 있어야 두 도메인이 서로를 참조하지 않는다.
 */
class SlotKindBenefitScopeTest {

    @Test
    void 숙소_혜택은_숙박_슬롯에_붙는다() {
        assertEquals(SlotKind.STAY, SlotKind.covering(BenefitScope.LODGING));
    }

    @Test
    void 숙박세일페스타는_숙박_슬롯까지_이어진다() {
        // 두 도메인을 갈라 놓아도 사용자가 보는 사실(숙소 카드에 "숙박 할인")은 그대로여야 한다.
        assertEquals(SlotKind.STAY, SlotKind.covering(PolicyType.STAY_FESTA.targetScope().orElseThrow()));
    }
}
