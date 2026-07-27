package com.offway.core.weather.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GridTest {

    @Test
    void 서울시청은_격자_60_127로_변환된다() {
        Grid grid = Grid.from(37.5665, 126.9780);

        assertEquals(60, grid.nx());
        assertEquals(127, grid.ny());
    }

    @Test
    void 부산시청은_격자_98_76으로_변환된다() {
        Grid grid = Grid.from(35.1796, 129.0756);

        assertEquals(98, grid.nx());
        assertEquals(76, grid.ny());
    }
}
