package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RegionVisitorStatTest {

    @Test
    void 일평균은_총방문자를_관측일수로_나눈값이다() {
        RegionVisitorStat stat = new RegionVisitorStat(1L, 120000, 30, true);
        assertEquals(4000.0, stat.meanDaily());
    }

    @Test
    void 관측일수가_0이면_일평균은_0이다() {
        RegionVisitorStat stat = new RegionVisitorStat(1L, 0, 0, true);
        assertEquals(0.0, stat.meanDaily());
    }

    @Test
    void 음수_방문자나_음수_관측일수는_불변식_위반이다() {
        assertThrows(IllegalArgumentException.class, () -> new RegionVisitorStat(1L, -1, 30, true));
        assertThrows(IllegalArgumentException.class, () -> new RegionVisitorStat(1L, 100, -1, true));
    }

    @Test
    void 비유한_방문자수는_불변식_위반이다() {
        assertThrows(IllegalArgumentException.class, () -> new RegionVisitorStat(1L, Double.NaN, 30, true));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RegionVisitorStat(1L, Double.POSITIVE_INFINITY, 30, true));
    }

    @Test
    void 관측일수가_0인데_방문자수가_0이_아니면_불변식_위반이다() {
        assertThrows(IllegalArgumentException.class, () -> new RegionVisitorStat(1L, 120000, 0, true));
    }
}
