package com.offway.core.transport.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * 구간 소요시간 기록의 상태 전이(#107 · #97).
 *
 * <p>"아직 안 쟀다" 와 "재봤더니 운행 없다" 를 가르는 것이 핵심이다. 뭉개면 배치가 같은 구간을 영원히
 * 다시 재거나(전자로 굳으면), 멀쩡한 구간을 영원히 포기한다(후자로 굳으면).
 */
class TransitLegDurationTest {

    private static final LocalDateTime 요청시각 = LocalDateTime.of(2026, 8, 31, 10, 0);
    private static final LocalDateTime 측정시각 = LocalDateTime.of(2026, 8, 31, 11, 17);

    private static TransitLegDuration 요청됨() {
        return TransitLegDuration.requested(
                TransitMode.INTERCITY_BUS, "NAI0511601", "NAI2613201", 요청시각);
    }

    @Test
    void 자리만_만든_구간은_아직_안_잰_것이다() {
        TransitLegDuration leg = 요청됨();

        assertTrue(leg.isUnmeasured());
        assertTrue(leg.usableMinutes().isEmpty());
    }

    @Test
    void 잰_구간은_소요시간을_준다() {
        TransitLegDuration leg = 요청됨();

        leg.measured(new MeasuredLeg(150, 28_600, "우등"), 측정시각);

        assertFalse(leg.isUnmeasured());
        assertEquals(150, leg.usableMinutes().orElseThrow());
        assertEquals(28_600, leg.getCharge());
        assertEquals("우등", leg.getVehicleName());
    }

    @Test
    void 운행이_없다는_것도_결과라_다시_재지_않는다() {
        // 여기서 isUnmeasured 가 계속 true 면 배치가 이 구간을 영원히 다시 잰다 — 한도가 그만큼 샌다.
        TransitLegDuration leg = 요청됨();

        leg.measured(null, 측정시각);

        assertFalse(leg.isUnmeasured());
        assertTrue(leg.usableMinutes().isEmpty());
    }

    @Test
    void 다시_재면_옛_값이_남지_않는다() {
        // 운행이 끊긴 구간에 옛 소요시간이 남으면 코스가 없는 배를 태운다.
        TransitLegDuration leg = 요청됨();
        leg.measured(new MeasuredLeg(150, 28_600, "우등"), 측정시각);

        leg.measured(null, 측정시각.plusDays(1));

        assertTrue(leg.usableMinutes().isEmpty());
        assertEquals(null, leg.getCharge());
        assertEquals(null, leg.getVehicleName());
    }

    @Test
    void 소요시간이_0분_이하인_구간은_만들_수_없다() {
        // 시각 파싱이 어긋났거나 응답이 뒤집힌 것이라 값으로 받아들이면 안 된다.
        assertThrows(IllegalArgumentException.class, () -> new MeasuredLeg(0, null, null));
        assertThrows(IllegalArgumentException.class, () -> new MeasuredLeg(-30, null, null));
    }

    @Test
    void 잰_구간이_비어_있으면_Measured_가_아니라_NoService_다() {
        assertThrows(IllegalArgumentException.class, () -> new TransitLegResult.Measured(null));
    }
}
