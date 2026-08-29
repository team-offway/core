package com.offway.core.transport.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UnroutableReasonTest {

    @Test
    void 도로_링크_없음은_1100_이다() {
        assertEquals(Optional.of(UnroutableReason.NO_ROAD_LINK), UnroutableReason.fromTmapCode("1100"));
    }

    @Test
    void 한반도_범위_초과는_1009_이다() {
        assertEquals(Optional.of(UnroutableReason.OUT_OF_BOUNDS), UnroutableReason.fromTmapCode("1009"));
    }

    @Test
    void 앞뒤_공백은_무시한다() {
        assertEquals(Optional.of(UnroutableReason.NO_ROAD_LINK), UnroutableReason.fromTmapCode(" 1100 "));
    }

    /**
     * <b>모르는 code 를 좌표 탓으로 몰지 않는다.</b> 그러면 일시적 오류 한 번에 멀쩡한 장소가 영구히
     * 코스에서 사라진다 — 틀린 이동시간이 한 번 더 나가는 것보다 나쁘다.
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "9999", "1101", "110", "NO_ROAD_LINK"})
    void 모르는_code_는_좌표_탓이_아니다(String code) {
        assertTrue(UnroutableReason.fromTmapCode(code).isEmpty());
    }
}
