package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RegionRankingTest {

    @Test
    void 빈_입력은_빈_랭킹이다() {
        assertTrue(RegionRanking.rank(List.of()).isEmpty());
    }

    @Test
    void 방문자가_많은_지역이_상위다() {
        RegionVisitorStat many = new RegionVisitorStat(1L, 300000, 30, PopulationDeclineStatus.TARGET); // 일 10000
        RegionVisitorStat few = new RegionVisitorStat(2L, 60000, 30, PopulationDeclineStatus.TARGET); // 일 2000

        List<RegionScore> ranked = RegionRanking.rank(List.of(few, many));

        assertEquals(1L, ranked.get(0).regionId());
        assertEquals(2L, ranked.get(1).regionId());
    }

    @Test
    void 혼잡도_뱃지는_실측_일평균으로_붙는다() {
        RegionVisitorStat crowded = new RegionVisitorStat(1L, 300000, 30, PopulationDeclineStatus.TARGET); // 일 10000 → HIGH
        RegionVisitorStat quiet = new RegionVisitorStat(2L, 60000, 30, PopulationDeclineStatus.TARGET); // 일 2000 → LOW

        List<RegionScore> ranked = RegionRanking.rank(List.of(crowded, quiet));

        RegionScore c = ranked.stream().filter(s -> s.regionId() == 1L).findFirst().orElseThrow();
        RegionScore q = ranked.stream().filter(s -> s.regionId() == 2L).findFirst().orElseThrow();
        assertEquals(CrowdLevel.HIGH, c.crowdLevel());
        assertEquals(CrowdLevel.LOW, q.crowdLevel());
    }

    @Test
    void 베이지안_보정은_표본이_적은_로컬을_묻지_않는다() {
        // sparseLocal·steadyLow 는 실측 일평균이 500 으로 같지만 표본이 다르다(2일 vs 30일).
        // 표본이 적은 sparseLocal 이 글로벌 평균 쪽으로 더 당겨져 steadyLow 위로 올라온다.
        RegionVisitorStat sparseLocal = new RegionVisitorStat(3L, 1000, 2, PopulationDeclineStatus.TARGET); // 일 500, 2일
        RegionVisitorStat steadyLow = new RegionVisitorStat(5L, 15000, 30, PopulationDeclineStatus.TARGET); // 일 500, 30일
        RegionVisitorStat popular = new RegionVisitorStat(4L, 120000, 30, PopulationDeclineStatus.TARGET); // 일 4000

        List<RegionScore> ranked = RegionRanking.rank(List.of(steadyLow, sparseLocal, popular));

        assertEquals(List.of(4L, 3L, 5L), ranked.stream().map(RegionScore::regionId).toList());
    }

    @Test
    void 글로벌_prior는_관측일수로_가중한_pooled_평균이다() {
        // 동일가중 평균이면 소표본(smallLow)이 prior 를 부풀려 bigSteady 가 smallLow 밑으로 가지만,
        // pooled(총방문자/총관측일수) prior 에선 bigSteady 가 smallLow 위로 온다.
        RegionVisitorStat bigSteady = new RegionVisitorStat(11L, 300000, 100, PopulationDeclineStatus.TARGET); // 일 3000, 100일
        RegionVisitorStat smallHigh = new RegionVisitorStat(10L, 20000, 2, PopulationDeclineStatus.TARGET); // 일 10000, 2일
        RegionVisitorStat smallLow = new RegionVisitorStat(12L, 2000, 2, PopulationDeclineStatus.TARGET); // 일 1000, 2일

        List<RegionScore> ranked = RegionRanking.rank(List.of(bigSteady, smallHigh, smallLow));

        assertEquals(List.of(10L, 11L, 12L), ranked.stream().map(RegionScore::regionId).toList());
    }

    @Test
    void 인구감소_가점은_동일조건에서_상위로_올린다() {
        RegionVisitorStat decline = new RegionVisitorStat(6L, 60000, 30, PopulationDeclineStatus.TARGET);
        RegionVisitorStat notDecline = new RegionVisitorStat(7L, 60000, 30, PopulationDeclineStatus.NON_TARGET);

        List<RegionScore> ranked = RegionRanking.rank(List.of(notDecline, decline));

        assertEquals(6L, ranked.get(0).regionId());
        assertTrue(ranked.get(0).score() > ranked.get(1).score());
    }
}
