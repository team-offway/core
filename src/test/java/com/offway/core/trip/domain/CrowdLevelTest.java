package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CrowdLevelTest {

    @ParameterizedTest
    @CsvSource({
        "0, LOW",
        "2999, LOW",
        "3000, MID",
        "9999, MID",
        "10000, HIGH",
        "50000, HIGH"
    })
    void 실측_일평균_방문자로_혼잡도_뱃지를_가른다(double meanDaily, CrowdLevel expected) {
        assertEquals(expected, CrowdLevel.of(meanDaily));
    }
}
