package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 방문자 집계 도메인(#193). 랭킹 가중치의 근거라 값이 틀어지면 추천 순위가 조용히 어긋난다.
 */
class RegionVisitorAggregateTest {

    private static final YearMonth JUNE = YearMonth.of(2026, 6);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 6, 15, 0);

    private static RegionVisitorAggregate of(String code, double total, int days) {
        return RegionVisitorAggregate.of(code, JUNE, total, days, NOW);
    }

    @Test
    void 시군구코드와_기준달과_집계를_들고_있다() {
        RegionVisitorAggregate aggregate = of("44150", 12_345.5, 7);

        assertEquals("44150", aggregate.getSignguCode());
        assertEquals(12_345.5, aggregate.getVisitorTotal());
        assertEquals(7, aggregate.getObservedDays());
        assertEquals(JUNE, aggregate.baseMonth(), "저장은 문자열이지만 갱신 판단은 달로 한다");
    }

    @ParameterizedTest(name = "방문자 합 {0} 은 거부")
    @ValueSource(doubles = {-0.1, -1000})
    void 방문자_합은_음수일_수_없다(double total) {
        // 음수가 들어오면 베이지안 보정이 뒤집혀 그 지역이 실제보다 높게 올라간다.
        assertThrows(IllegalArgumentException.class, () -> of("44150", total, 7));
    }

    @Test
    void 관측_일수는_음수일_수_없다() {
        assertThrows(IllegalArgumentException.class, () -> of("44150", 100, -1));
    }

    @Test
    void 관측_일수_0은_허용한다() {
        // 표본이 없다는 뜻이라 유효한 상태다 — 베이지안 prior 가 전국 평균으로 대신한다.
        assertEquals(0, of("44150", 0, 0).getObservedDays());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 시군구코드가_없으면_거부한다(String code) {
        // 코드가 비면 어느 지역 집계인지 알 수 없어 전 지역이 방문자 0으로 떨어진다.
        assertThrows(RuntimeException.class, () -> of(code, 100, 7));
    }

    @Test
    void 기준달과_갱신시각은_필수다() {
        assertThrows(NullPointerException.class,
                () -> RegionVisitorAggregate.of("44150", null, 100, 7, NOW));
        assertThrows(NullPointerException.class,
                () -> RegionVisitorAggregate.of("44150", JUNE, 100, 7, null));
    }
}
