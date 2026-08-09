package com.offway.core.trip.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.trip.domain.RegionVisitorAggregate;
import com.offway.core.trip.service.RegionRankingService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장된 방문자 집계의 <b>기준 연월 판정</b>(#193) — 워머가 외부를 부를지 말지를 이 값 하나가 정한다.
 *
 * <p>틀려도 화면은 멀쩡해 보인다. 옛 달을 최신으로 잘못 읽으면 갱신이 <b>영영</b> 멈추고, 반대면 배포마다 다시 긁어
 * 일일 한도를 태운다 — 둘 다 조용해서 로그를 뒤지기 전엔 모른다. 그래서 여기에 고정한다.
 */
@SpringBootTest
@Transactional
class RegionVisitorAggregateLatestMonthIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 6, 0, 0);

    @Autowired
    private RegionVisitorAggregateRepository aggregateRepository;

    @Autowired
    private RegionRankingService regionRankingService;

    private static RegionVisitorAggregate row(String code, YearMonth month) {
        return RegionVisitorAggregate.of(code, month, 1_000, 7, NOW);
    }

    @Test
    void 서로_다른_달이_섞여_있어도_가장_최근_달을_준다() {
        YearMonth june = YearMonth.of(2026, 6);
        // 전량 교체라 평상시엔 한 달만 남지만, 그 전제가 깨진 상태를 만든다 — 정렬 없이 첫 행을 집으면
        // 저장 순서에 따라 5월이 잡혀 "이미 최신" 으로 잘못 판단한다.
        aggregateRepository.replaceAll(List.of(
                row("44150", YearMonth.of(2026, 5)),
                row("47730", june),
                row("46870", YearMonth.of(2026, 4))));

        assertEquals(Optional.of(june), aggregateRepository.latestBaseMonth());
    }

    @Test
    void 저장이_비어_있으면_기준_연월이_없다() {
        aggregateRepository.replaceAll(List.of());

        assertEquals(Optional.empty(), aggregateRepository.latestBaseMonth());
        assertFalse(regionRankingService.hasLatest());
    }

    @Test
    void 저장분이_지난달_것이면_더_새_것은_없으므로_최신이다() {
        // 원본은 완결된 달만 발행하므로 지난달 것이 곧 최신 발행분이다.
        aggregateRepository.replaceAll(List.of(row("44150", lastMonth())));

        assertTrue(regionRankingService.hasLatest());
    }

    @Test
    void 저장분이_두_달_전_것이면_최신이_아니다() {
        aggregateRepository.replaceAll(List.of(row("44150", lastMonth().minusMonths(1))));

        assertFalse(regionRankingService.hasLatest());
    }

    @Test
    void 건수는_행을_적재하지_않고_센다() {
        aggregateRepository.replaceAll(List.of(row("44150", lastMonth()), row("47730", lastMonth())));

        assertEquals(2, aggregateRepository.count());
    }

    private static YearMonth lastMonth() {
        return YearMonth.from(LocalDate.now()).minusMonths(1);
    }
}
