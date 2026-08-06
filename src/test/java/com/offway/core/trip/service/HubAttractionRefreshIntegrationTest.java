package com.offway.core.trip.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.HubAttraction;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.datalab.HubAttractionClient;
import com.offway.core.trip.infrastructure.datalab.StubHubAttractionClient;
import com.offway.core.trip.infrastructure.datalab.dto.HubAttractionItem;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
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

        List<HubAttraction> stored = refreshService.forRegion(anyRegionId());
        assertEquals(2, stored.size());
        assertEquals(1, stored.getFirst().getHubRank());
        assertEquals("공산성", stored.getFirst().getName());
        assertEquals("역사관광", stored.getFirst().getCategoryMedium());
        assertTrue(stored.getFirst().hasCoordinate(), "좌표가 없으면 슬롯에 넣거나 다른 소스와 이을 수 없다");
    }

    @Test
    void 이미_최신이면_외부를_부르지_않는다() {
        // 원본은 월 단위로 갱신된다 — 지난달 것을 갖고 있으면 더 새 것은 존재하지 않는다.
        hubAttractionClient.respond((legalCode, month) -> List.of(item(1, "공산성", "관광지", "역사관광")));
        refreshService.refresh();

        hubAttractionClient.respond((legalCode, month) -> {
            throw new AssertionError("이미 최신인데 외부를 불렀다");
        });
        refreshService.refreshIfStale();
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
