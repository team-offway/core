package com.offway.core.policy.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.itinerary.domain.SlotKind;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 혜택이 어느 슬롯에 붙는가(#140) — <b>단정할 수 있는 것만</b>.
 *
 * <p>근거 없이 배지를 붙이면 사용자가 못 받는 할인을 기대하고 간다. 안 붙이는 것보다 나쁘다.
 */
class PolicyTypeSlotTargetTest {

    @Test
    void 숙박세일페스타는_숙소에_붙는다() {
        // 프로그램 이름이 곧 대상이라 단정할 수 있다. 89곳 중 85곳에 걸려 있어 대부분의 코스에 뜬다.
        assertEquals(Optional.of(SlotKind.STAY), PolicyType.STAY_FESTA.targetSlotKind());
    }

    @ParameterizedTest
    @EnumSource(value = PolicyType.class, names = "STAY_FESTA", mode = EnumSource.Mode.EXCLUDE)
    void 나머지는_슬롯을_단정하지_않는다(PolicyType type) {
        // 지자체 바우처는 가맹점 목록이, 디지털관광주민증은 제휴처 목록이 있어야 한다. 우리에겐 없다.
        assertTrue(type.targetSlotKind().isEmpty(), type + " 가 근거 없이 슬롯을 단정한다");
    }

    @Test
    void 슬롯을_단정하지_않아도_지역_혜택은_그대로다() {
        // 여기서 비어 있다고 혜택이 없는 게 아니다 — 코스 benefits 로는 계속 나간다.
        assertTrue(Arrays.stream(PolicyType.values()).allMatch(type -> type.targetTag() != null));
    }
}
