package com.offway.core.trip.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.Category;
import com.offway.core.trip.domain.RegionContent;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.tour.StubTourApiClient;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourPoi;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiResult;
import com.offway.core.trip.repository.RegionContentRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 지역 콘텐츠 DB 적재(#193 2단계).
 *
 * <p>여기서 지키는 것은 <b>"요청 경로가 외부를 부르지 않는가"</b> 다. 예전에는 캐시가 비면(배포 직후) 홈·추천이
 * 89곳 팬아웃을 요청 스레드에서 돌렸다.
 */
@SpringBootTest
class RegionContentRefreshIntegrationTest {

    @Autowired
    private RegionContentRefreshService refreshService;

    @Autowired
    private RegionContentProvider regionContentProvider;

    @Autowired
    private RegionContentRepository regionContentRepository;

    @Autowired
    private StubTourApiClient tourApiClient;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private com.offway.core.common.batch.repository.BatchRunRepository batchRunRepository;

    /**
     * 이 배치가 "이번 주에는 아직 안 돌았다" 인 상태로 만든다.
     *
     * <p>클래스에 {@code @Transactional} 이 없어 실행 기록이 커밋된다. 앞 테스트가 남긴 기록이 뒤 테스트를
     * 통째로 건너뛰게 하므로, 각 테스트가 자기 전제를 직접 만든다.
     */
    private void notRunThisWeek() {
        batchRunRepository.markStarted(
                RegionContentRefreshService.BATCH_NAME, java.time.LocalDateTime.now().minusDays(30));
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        TourApiClient stubTourApiClient() {
            return new StubTourApiClient();
        }
    }

    private List<Long> allRegionIds() {
        return regionRepository.findAll().stream().map(Region::getId).toList();
    }

    private Long anyRegionId() {
        return regionRepository.findAll().getFirst().getId();
    }

    /** 볼거리 수만 다르게 주는 TourAPI 응답 — 대분류 NA 라 카테고리는 관광지로 잡힌다. */
    private static TourPoiResult poiResult(int totalCount) {
        TourPoi poi = new TourPoi(
                "126508", 12, "NA", "가사동백숲해변", "전남 완도군", 34.36, 126.92, "http://img/a.jpg", null, null);
        return new TourPoiResult(List.of(poi), totalCount);
    }

    @Test
    void 적재하면_요청_경로가_DB_만_읽는다() {
        regionContentProvider.evictCache();
        tourApiClient.respond(() -> poiResult(20));
        refreshService.refresh();

        // 적재 후에는 외부가 죽어도 조회가 된다 — 요청 경로가 DB 만 보기 때문이다.
        tourApiClient.respond(() -> {
            throw TourApiException.lookupFailed(new RuntimeException("upstream down"));
        });
        Map<Long, RegionContent> contents = regionContentProvider.storedForAll(allRegionIds());

        assertFalse(contents.isEmpty());
        assertEquals(20, contents.get(anyRegionId()).contentCount());
    }

    @Test
    void 카테고리와_인접_병합_표시가_보존된다() {
        regionContentProvider.evictCache();
        tourApiClient.respond(() -> poiResult(3));
        refreshService.refresh();

        RegionContent stored = regionContentProvider.storedForAll(allRegionIds()).get(anyRegionId());

        // 볼거리가 기준 미만이라 인접 50km 가 병합된다 — 그 사실이 저장에서 살아남아야 화면 안내가 맞는다.
        assertTrue(stored.neighborIncluded(), "인접 병합 표시가 저장·복원돼야 한다");
        assertTrue(stored.categories().contains(Category.SIGHT), "대분류 NA 는 관광지로 잡힌다");
    }

    @Test
    void 조회가_실패해도_이전_값이_그대로_남는다() {
        regionContentProvider.evictCache();
        tourApiClient.respond(() -> poiResult(15));
        refreshService.refresh();
        RegionContent before = regionContentProvider.storedForAll(allRegionIds()).get(anyRegionId());

        regionContentProvider.evictCache();
        tourApiClient.respond(() -> {
            throw TourApiException.lookupFailed(new RuntimeException("upstream down"));
        });
        refreshService.refresh();

        // 행 수만 보면 안 된다 — degrade 는 빈 콘텐츠를 주므로 행은 그대로인 채 <b>값만</b> 비워질 수 있다.
        RegionContent after = regionContentProvider.storedForAll(allRegionIds()).get(anyRegionId());
        assertEquals(before.contentCount(), after.contentCount(), "갱신 실패가 데이터 손실이 되면 안 된다");
        assertEquals(before.imageUrl(), after.imageUrl());
        assertEquals(before.categories(), after.categories());
    }

    @Test
    void 적재_전에는_키가_없고_호출자가_빈_콘텐츠로_본다() {
        regionContentRepository.replaceAll(List.of());

        assertTrue(regionContentProvider.storedForAll(allRegionIds()).isEmpty(),
                "적재 전이면 키가 없다 — 그 자리에서 89곳을 긁어 사용자를 기다리게 하지 않는다");
    }

    /**
     * 이번 주에 이미 돌았으면 <b>외부를 아예 안 부른다</b>.
     *
     * <p>예전에는 건너뛰기가 없어 부팅마다 89곳 × (자기 + 인접) ≈ 130회를 다시 쐈다. 배포가 잦은 날은
     * 그만큼 곱해져 TourAPI 일일 한도를 태웠고, 같은 키를 쓰는 코스 생성·장소 상세까지 함께 막혔다.
     */
    @Test
    void 이번_주에_이미_돌았으면_외부를_부르지_않는다() {
        notRunThisWeek();
        tourApiClient.respond(() -> poiResult(20));
        refreshService.refreshIfStale();

        tourApiClient.respond(() -> {
            throw new AssertionError("이번 주에 이미 돌았는데 TourAPI 를 불렀다");
        });
        refreshService.refreshIfStale();
    }

    @Test
    void 전부_실패한_회차에도_같은_주에_다시_부르지_않는다() {
        // 폭주를 끊는 핵심이다. 결과로 판정하면 전부 실패한 회차에는 아무것도 안 써져 재부팅마다 또 130회다.
        notRunThisWeek();
        tourApiClient.respond(() -> {
            throw TourApiException.lookupFailed(new RuntimeException("429 Too Many Requests"));
        });
        refreshService.refreshIfStale();

        tourApiClient.respond(() -> {
            throw new AssertionError("전부 실패했다고 같은 주에 다시 부르면 한도만 더 태운다");
        });
        refreshService.refreshIfStale();
    }
}
