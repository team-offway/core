package com.offway.core.transport.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 수단별 간선 속도(#58) — <b>거점과 거점 사이</b>를 얼마나 빨리 잇는가.
 *
 * <p>여기서 잠그는 것은 정확한 숫자가 아니라 <b>수단 사이의 서열</b>이다. 열차가 시외버스보다 느려지면
 * 추천이 엉뚱한 수단을 고르고, 도달 필터가 통째로 틀어진다.
 */
class TransitModeTrunkSpeedTest {

    /**
     * <b>빠른 순서가 뒤집히면 안 된다.</b> 열차 > 고속버스 > 여객선 > 시외버스.
     *
     * <p>여객선이 시외버스보다 빠른 것이 뜻밖으로 보이지만, 바다는 신호도 정차도 없다 — 포항→울릉
     * 쾌속선이 63㎞/h 인 반면 시외버스는 국도와 터미널 정차가 섞여 50㎞/h 다.
     */
    @Test
    void 수단_사이의_빠른_순서가_유지된다() {
        double train = TransitMode.TRAIN.trunkSpeedKmh();
        double express = TransitMode.EXPRESS_BUS.trunkSpeedKmh();
        double ferry = TransitMode.FERRY.trunkSpeedKmh();
        double intercity = TransitMode.INTERCITY_BUS.trunkSpeedKmh();

        assertTrue(train > express, "열차가 고속버스보다 느리면 추천이 엉뚱한 수단을 고른다");
        assertTrue(express > ferry);
        assertTrue(ferry > intercity);
    }

    /**
     * 역산 근거와 맞는지 본다 — 서울경부→태백은 직선 179㎞ 에 실제 170분이었다(#443 실측).
     *
     * <p>추정이 실제의 ±25% 안에 들면 도달 필터로 쓸 만하다. 그보다 벌어지면 못 가는 곳이 후보에
     * 오르거나 갈 수 있는 곳이 빠진다.
     */
    @Test
    void 고속버스_추정이_실제_노선과_크게_안_어긋난다() {
        int estimated = TransitMode.EXPRESS_BUS.trunkMinutes(179.2);

        assertTrue(estimated > 170 * 0.75 && estimated < 170 * 1.25,
                "서울경부→태백 실제 170분인데 추정이 " + estimated + "분이다");
    }

    /** 열차도 같은 방식으로 — 청량리→안동 무궁화는 직선 186㎞ 에 210분이었다. */
    @Test
    void 열차_추정이_실제_노선_범위에_든다() {
        int estimated = TransitMode.TRAIN.trunkMinutes(186.4);

        // KTX(141㎞/h)와 무궁화(53㎞/h) 사이라 폭이 넓다. 그 사이에만 들면 된다.
        assertTrue(estimated > 79 && estimated < 211,
                "서울→부산 KTX 79분과 청량리→안동 무궁화 210분 사이여야 한다: " + estimated);
    }

    @ParameterizedTest
    @EnumSource(value = TransitMode.class, names = {"TRAIN", "EXPRESS_BUS", "INTERCITY_BUS", "FERRY"})
    void 거리가_0이면_0분이다(TransitMode mode) {
        assertEquals(0, mode.trunkMinutes(0));
    }

    @ParameterizedTest
    @EnumSource(value = TransitMode.class, names = {"TRAIN", "EXPRESS_BUS", "INTERCITY_BUS", "FERRY"})
    void 거리가_음수거나_유한하지_않으면_거절한다(TransitMode mode) {
        assertThrows(IllegalArgumentException.class, () -> mode.trunkMinutes(-1));
        assertThrows(IllegalArgumentException.class, () -> mode.trunkMinutes(Double.NaN));
    }

    /**
     * 반올림 결과가 int 를 넘으면 거절한다.
     *
     * <p>지구 둘레가 4만㎞ 라 실제 좌표로는 닿지 않는다. 다만 넘으면 {@code (int)} 변환이 조용히 음수를
     * 만들고, <b>도달시간이 음수면 추천이 그 지역을 "가장 가깝다" 로 읽는다</b>.
     */
    @ParameterizedTest
    @EnumSource(value = TransitMode.class, names = {"TRAIN", "EXPRESS_BUS", "INTERCITY_BUS", "FERRY"})
    void 소요시간이_표현_범위를_넘으면_거절한다(TransitMode mode) {
        assertThrows(IllegalArgumentException.class, () -> mode.trunkMinutes(Double.MAX_VALUE));
    }

    /**
     * <b>자차는 간선이 없다.</b> 거점을 거치지 않으므로 이 값을 물으면 그것이 곧 호출부의 버그다 —
     * {@code lookaheadDays()} 가 같은 이유로 던진다.
     */
    @Test
    void 자차는_간선_속도를_묻지_못한다() {
        assertThrows(IllegalStateException.class, TransitMode.CAR::trunkSpeedKmh);
    }
}
