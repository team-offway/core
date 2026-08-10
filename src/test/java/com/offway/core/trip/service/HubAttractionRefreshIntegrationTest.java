package com.offway.core.trip.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.HubAttraction;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.datalab.HubAttractionClient;
import com.offway.core.trip.infrastructure.datalab.StubHubAttractionClient;
import com.offway.core.trip.infrastructure.datalab.dto.HubAttractionItem;
import com.offway.core.trip.repository.HubAttractionRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 중심 관광지 적재(#185) 통합 테스트.
 *
 * <p>외부는 stub 으로 격리한다. 적재는 DB 를 건드리므로 각 테스트가 자기 시나리오를 직접 만든다.
 */
@SpringBootTest
class HubAttractionRefreshIntegrationTest {

    @Autowired
    private HubAttractionRefreshService refreshService;

    @Autowired
    private StubHubAttractionClient hubAttractionClient;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private HubAttractionRepository hubAttractionRepository;

    @Autowired
    private com.offway.core.common.batch.repository.BatchRunRepository batchRunRepository;

    /**
     * 이 배치가 "오늘은 아직 안 돌았다" 인 상태로 만든다.
     *
     * <p>클래스에 {@code @Transactional} 이 없어 적재가 커밋된다. 실행 기록도 마찬가지라, 앞 테스트가
     * 남긴 오늘 기록이 뒤 테스트를 통째로 건너뛰게 만든다 — 각 테스트가 자기 전제를 직접 만든다.
     */
    private void notRunToday() {
        batchRunRepository.markStarted(
                HubAttractionRefreshService.BATCH_NAME, LocalDate.now().minusDays(1).atTime(3, 0));
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        HubAttractionClient stubHubAttractionClient() {
            return new StubHubAttractionClient();
        }
    }

    private static YearMonth lastMonth() {
        return YearMonth.from(LocalDate.now()).minusMonths(1);
    }

    private static HubAttractionItem item(int rank, String name, String large, String medium) {
        return new HubAttractionItem(rank, "code-" + rank, name, large, medium, 36.46, 127.12);
    }

    private Long anyRegionId() {
        return regionRepository.findAll().getFirst().getId();
    }

    @Test
    void 전_지역을_순위대로_적재한다() {
        hubAttractionClient.respond((legalCode, month) ->
                List.of(item(1, "공산성", "관광지", "역사관광"), item(2, "산성시장", "관광지", "쇼핑")));

        refreshService.refresh();

        // 한 지역만 보면 루프가 첫 지역 뒤에 끝나도 통과한다 — 이름대로 전 지역을 확인한다.
        List<Region> regions = regionRepository.findAll();
        assertFalse(regions.isEmpty());
        for (Region region : regions) {
            List<HubAttraction> stored = refreshService.forRegion(region.getId());
            assertEquals(2, stored.size(), "regionId=" + region.getId());
            assertEquals(1, stored.getFirst().getHubRank(), "regionId=" + region.getId());
            assertEquals("공산성", stored.getFirst().getName(), "regionId=" + region.getId());
            assertEquals("역사관광", stored.getFirst().getCategoryMedium(), "regionId=" + region.getId());
            assertTrue(stored.getFirst().hasCoordinate(), "좌표가 없으면 슬롯에 넣거나 다른 소스와 이을 수 없다");
        }
    }

    @Test
    void 발행이_최대치까지_밀려도_그_달을_찾는다() {
        // MAX_MONTHS_BACK 이 3이면 3개월 전 달까지 봐야 한다 — 경계를 빼면 그 달만 있는 시점에 갱신이 멈춘다.
        YearMonth published = lastMonth().minusMonths(3);
        hubAttractionClient.respond((legalCode, month) ->
                month.equals(published) ? List.of(item(1, "공산성", "관광지", "역사관광")) : List.of());

        refreshService.refresh();

        assertEquals(published, refreshService.forRegion(anyRegionId()).getFirst().baseMonth());
    }

    @Test
    void 한_지역이_실패해도_나머지는_채운다() {
        List<Region> regions = regionRepository.findAll();
        regions.forEach(region -> hubAttractionRepository.replaceRegion(region.getId(), List.of()));
        String firstCode = regions.getFirst().getLegalCode();

        hubAttractionClient.respond((legalCode, month) -> {
            if (legalCode.equals(firstCode)) {
                throw TourApiException.lookupFailed(new RuntimeException("upstream down"));
            }
            return List.of(item(1, "공산성", "관광지", "역사관광"));
        });

        refreshService.refresh();

        assertFalse(refreshService.forRegion(regions.get(1).getId()).isEmpty(),
                "한 지역이 실패했다고 나머지 지역 갱신까지 포기하면 안 된다");
    }

    @Test
    void 지역마다_발행월이_달라도_각자_자기_달로_채운다() {
        // 실측(2026-08-09) — 전남 16곳은 202607 이 미발행이고 202606 에만 있는데 나머지 지역은 202607 이
        // 있었다. 표본 몇 곳으로 달 하나를 정해 전 지역에 쓰면 전남이 통째로 빈 응답이 되고, 빈 응답은 이전
        // 값을 유지하므로 첫 적재에서 그 16곳이 영영 안 채워진다.
        List<Region> regions = regionRepository.findAll();
        regions.forEach(region -> hubAttractionRepository.replaceRegion(region.getId(), List.of()));
        YearMonth newest = lastMonth();
        YearMonth older = newest.minusMonths(1);
        String lateCode = regions.getLast().getLegalCode();

        hubAttractionClient.respond((legalCode, month) -> {
            boolean late = legalCode.equals(lateCode);
            YearMonth published = late ? older : newest;
            return month.equals(published) ? List.of(item(1, "공산성", "관광지", "역사관광")) : List.of();
        });

        refreshService.refresh();

        assertEquals(newest, refreshService.forRegion(regions.getFirst().getId()).getFirst().baseMonth());
        assertEquals(older, refreshService.forRegion(regions.getLast().getId()).getFirst().baseMonth(),
                "한 달 늦게 발행되는 지역도 자기 달로 채워져야 한다");
    }

    @Test
    void 오늘_이미_돌았으면_외부를_부르지_않는다() {
        // 원본은 월 단위로 갱신되므로 하루 한 번이면 충분하다. 예전에는 "89곳이 전부 최신 달을 가졌는가"
        // 로 판정했는데, 지역마다 발행 시점이 달라 그 조건이 사실상 참이 되지 않아 부팅마다 89회를 쐈다.
        notRunToday();
        hubAttractionClient.respond((legalCode, month) -> List.of(item(1, "공산성", "관광지", "역사관광")));
        refreshService.refreshIfStale();

        hubAttractionClient.respond((legalCode, month) -> {
            throw new AssertionError("오늘 이미 돌았는데 외부를 불렀다");
        });
        refreshService.refreshIfStale();
    }

    @Test
    void 전부_실패한_날에도_같은_날_다시_부르지_않는다() {
        // 폭주를 끊는 핵심이다. 결과로 판정하면 전부 실패한 날에는 아무것도 안 써져 재부팅마다 또 89회를
        // 쏜다 — 실제 로그의 16:07·16:13 이 그것이다. 그래서 성공·실패를 가리지 않고 실행을 기록한다.
        notRunToday();
        hubAttractionClient.respond((legalCode, month) -> {
            throw TourApiException.lookupFailed(new RuntimeException("429 Too Many Requests"));
        });
        refreshService.refreshIfStale();

        hubAttractionClient.respond((legalCode, month) -> {
            throw new AssertionError("전부 실패했다고 같은 날 다시 부르면 한도만 더 태운다");
        });
        refreshService.refreshIfStale();
    }

    @Test
    void 어제_돌았으면_오늘_다시_부른다() {
        notRunToday();
        AtomicBoolean called = new AtomicBoolean();
        hubAttractionClient.respond((legalCode, month) -> {
            called.set(true);
            return List.of(item(1, "공산성", "관광지", "역사관광"));
        });

        refreshService.refreshIfStale();

        assertTrue(called.get(), "하루가 지나면 다시 받아야 한다");
    }

    @Test
    void 빈_결과로_이전_값을_덮지_않는다() {
        // 성공 코드에 빈 결과가 오는 API 다. 덮으면 그 지역 카드에서 대표 사진과 볼거리가 통째로 사라진다.
        hubAttractionClient.respond((legalCode, month) -> List.of(item(1, "공산성", "관광지", "역사관광")));
        refreshService.refresh();

        hubAttractionClient.respond((legalCode, month) -> List.of());
        refreshService.refresh();

        assertFalse(refreshService.forRegion(anyRegionId()).isEmpty(), "이전 값이 남아 있어야 한다");
    }

    @Test
    void 조회가_실패해도_이전_값이_남는다() {
        hubAttractionClient.respond((legalCode, month) -> List.of(item(1, "공산성", "관광지", "역사관광")));
        refreshService.refresh();

        hubAttractionClient.respond((legalCode, month) -> {
            throw TourApiException.lookupFailed(new RuntimeException("upstream down"));
        });
        refreshService.refresh();

        assertEquals("공산성", refreshService.forRegion(anyRegionId()).getFirst().getName());
    }

    @Test
    void 미발행이면_이전_달로_물러선다() {
        // 이번 달은 아직 집계 중이라 비어 있을 수 있다. 고정 지연을 가정하면 조용히 빈 결과가 된다.
        YearMonth published = lastMonth().minusMonths(1);
        hubAttractionClient.respond((legalCode, month) ->
                month.equals(published) ? List.of(item(1, "공산성", "관광지", "역사관광")) : List.of());

        refreshService.refresh();

        List<HubAttraction> stored = refreshService.forRegion(anyRegionId());
        assertEquals(published, stored.getFirst().baseMonth());
    }

    @Test
    void 숙박은_대표_사진감이_아니다() {
        // 정선군 1위는 콘도, 2위는 카지노다. 데이터는 맞지만 지역 카드에 걸 그림은 아니다.
        hubAttractionClient.respond((legalCode, month) ->
                List.of(item(1, "힐콘도", "숙박", "숙박"), item(2, "병방치 스카이워크", "관광지", "문화관광")));

        refreshService.refresh();

        List<HubAttraction> stored = refreshService.forRegion(anyRegionId());
        assertFalse(stored.getFirst().isSight(), "1위가 숙박이면 대표 사진에서 걸러야 한다");
        assertTrue(stored.get(1).isSight());
    }
}
