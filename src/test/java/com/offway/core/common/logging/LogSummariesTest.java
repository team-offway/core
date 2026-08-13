package com.offway.core.common.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class LogSummariesTest {

    @Test
    void 라벨과_건수를_낸다() {
        assertEquals("숙소 3건", LogSummaries.count("숙소", List.of("a", "b", "c")));
    }

    @Test
    void 빈_목록은_0건이다() {
        assertEquals("숙소 0건", LogSummaries.count("숙소", List.of()));
    }

    @Test
    void null_목록도_0건으로_본다() {
        assertEquals("숙소 0건", LogSummaries.count("숙소", null));
    }
}
