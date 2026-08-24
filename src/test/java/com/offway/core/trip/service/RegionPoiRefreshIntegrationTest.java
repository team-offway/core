package com.offway.core.trip.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.Category;
import com.offway.core.trip.domain.RegionPoi;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.tour.StubTourApiClient;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourPoi;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiResult;
import com.offway.core.trip.repository.RegionPoiRepository;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지역 장소 풀을 채우는 월 1회 배치(#304).
 *
 * <p>여기서 지키는 것은 <b>"요청 경로가 외부를 부르지 않는가"</b> 다. TourAPI 는 일 1,000건을 관광빅데이터와
 * 나눠 쓰므로, 지역 상세를 열 때마다 부르면 사용자 몇 명으로 한도가 마르고 코스 생성이 먼저 죽는다.
 *
 * <p>"DB 상태 + 결정" 을 보는 테스트다 — 이미 최신인 지역을 건너뛰는가, 빈 응답으로 덮지 않는가,
 * 갱신이 통째로 갈아 끼우는가. 단위로는 확인할 수 없다.
 *
 * <p><b>기준월을 시나리오마다 다르게 쓴다.</b> <b>이 배치의 건너뛰기 판정이 기준월</b>이라, 같은 달을
 * 쓰면 앞 테스트가 채워 둔 탓에 뒤 테스트가 통째로 건너뛰어져 <b>아무것도 저장되지 않는다</b> — 실제로
 * 그렇게 한 번 깨졌다. 다른 어떤 테스트도 쓰지 않을 먼 미래 달을 시나리오마다 따로 쓴다.
 *
 * <p><b>롤백은 클래스 레벨 {@code @Transactional} 이 한다</b>(테스트 규약). 이 클래스가 심는 장소와
 * 배치 실행 표식이 남으면, 같은 컨텍스트를 쓰는 뒤 테스트가 <b>실행 순서에 따라</b> 그것을 전제로 돌거나
 * 깨진다. 기준월을 나누는 것과는 다른 층의 방어다 — 그건 이 클래스 안의 충돌을, 이건 클래스 밖으로
 * 새는 것을 막는다.
 */
@SpringBootTest
@Transactional
class RegionPoiRefreshIntegrationTest {


    @Autowired
    private RegionPoiRefreshService refreshService;

    @Autowired
    private RegionPoiRepository regionPoiRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private StubTourApiClient tourApiClient;

    @Test
    void 사진과_분류를_담아_지역에_적재한다() {
        YearMonth baseYm = YearMonth.of(2099, 3);
        long regionId = anyRegionId();
        tourApiClient.respond(() -> result(
                poi("C-1", "NA", "범일 이중섭거리", "http://img/1.jpg"),
                poi("C-2", "AC", "정선 게스트하우스", "http://img/2.jpg")));

        refreshService.refresh(baseYm);

        List<RegionPoi> stored = regionPoiRepository.findShowable(regionId, 10);
        assertFalse(stored.isEmpty(), "적재된 장소가 없다");
        assertTrue(stored.stream().anyMatch(p -> p.getCategory() == Category.SIGHT), "관광지 분류가 없다");
        assertTrue(stored.stream().anyMatch(p -> p.getCategory() == Category.STAY), "숙박 분류가 없다");
    }

    /**
     * <b>사진 없는 장소는 저장하되 조회에서 빠진다.</b>
     *
     * <p>저장까지 막지 않는 이유는 나중에 사진이 붙을 수 있고, 홈 카드가 같은 풀을 쓰기 때문이다.
     * 거르는 자리는 조회 한 곳이라 세는 쪽과 내리는 쪽이 갈리지 않는다.
     */
    @Test
    void 사진_없는_장소는_매력_포인트에서_빠진다() {
        YearMonth baseYm = YearMonth.of(2099, 4);
        long regionId = anyRegionId();
        tourApiClient.respond(() -> result(
                poi("C-3", "NA", "사진 있는 곳", "http://img/3.jpg"),
                poi("C-4", "NA", "사진 없는 곳", null),
                poi("C-5", "NA", "사진이 빈 곳", "")));

        refreshService.refresh(baseYm);

        List<RegionPoi> shown = regionPoiRepository.findShowable(regionId, 10);
        assertTrue(shown.stream().allMatch(RegionPoi::showable), "사진 없는 장소가 섞였다");
        assertTrue(shown.stream().anyMatch(p -> "C-3".equals(p.getContentId())), "사진 있는 장소가 빠졌다");
    }

    /**
     * 그 달치가 이미 있으면 <b>외부를 아예 안 부른다</b>.
     *
     * <p>이게 이 배치의 한도 방어다. 호출 수를 직접 세어 확인한다 — "결과가 같다" 로는 안 부른 것과
     * 불렀는데 같은 값이 온 것이 구분되지 않는다.
     */
    @Test
    void 그_달치가_이미_있으면_외부를_부르지_않는다() {
        YearMonth baseYm = YearMonth.of(2099, 5);
        tourApiClient.respond(() -> result(poi("C-6", "NA", "한 번만 받는 곳", "http://img/6.jpg")));
        refreshService.refresh(baseYm);

        tourApiClient.resetAreaCallCount();
        refreshService.refresh(baseYm);

        assertEquals(0, tourApiClient.areaCallCount(), "이미 최신인데 외부를 다시 불렀다");
    }

    /**
     * 다음 달에는 다시 받는다 — 마커가 <b>기준월</b>이라 달이 바뀌면 낡은 것이 된다.
     */
    @Test
    void 달이_바뀌면_다시_받는다() {
        YearMonth baseYm = YearMonth.of(2099, 6);
        tourApiClient.respond(() -> result(poi("C-7", "NA", "이번 달", "http://img/7.jpg")));
        refreshService.refresh(baseYm);

        tourApiClient.resetAreaCallCount();
        refreshService.refresh(baseYm.plusMonths(1));

        assertTrue(tourApiClient.areaCallCount() > 0, "달이 바뀌었는데 외부를 안 불렀다");
    }

    /**
     * <b>빈 응답으로 덮지 않는다.</b>
     *
     * <p>외부가 실패해 0건이 왔는데 그대로 넣으면 멀쩡하던 지역 상세가 빈다. 낡은 목록이 없는 목록보다
     * 낫기 때문에 이전 값을 두고 다음 달을 기다린다.
     */
    @Test
    void 받아온_장소가_없으면_이전_값을_지우지_않는다() {
        YearMonth baseYm = YearMonth.of(2099, 8);
        long regionId = anyRegionId();
        tourApiClient.respond(() -> result(poi("C-8", "NA", "남아 있어야 하는 곳", "http://img/8.jpg")));
        refreshService.refresh(baseYm);
        int before = regionPoiRepository.findShowable(regionId, 10).size();

        tourApiClient.respond(() -> new TourPoiResult(List.of(), 0));
        refreshService.refresh(baseYm.plusMonths(1));

        // 개수만 보면 C-8 이 지워지고 다른 장소가 같은 수로 들어와도 통과한다 — 그 장소가 그대로인지 본다.
        List<RegionPoi> after = regionPoiRepository.findShowable(regionId, 10);
        assertEquals(before, after.size(), "빈 응답이 이전 값을 지웠다");
        assertTrue(after.stream().anyMatch(p -> "C-8".equals(p.getContentId())), "이전 장소가 다른 것으로 바뀌었다");
    }

    /**
     * 한 지역이 실패해도 나머지는 채운다.
     *
     * <p>배치가 89곳을 도는데 한 곳의 실패로 전체를 버리면 그달 전부가 빈 채로 남는다 — 다음 실행은
     * 한 달 뒤다.
     *
     * <p><b>첫 호출만 실패시킨다.</b> 모든 호출을 실패시키면 "실패해도 계속 돈다" 와 "첫 실패에서 멈춘다"
     * 가 같은 결과(0곳)를 내 구분되지 않는다. 뒤 지역이 실제로 채워졌는지까지 봐야 격리가 검증된다.
     */
    @Test
    void 한_지역이_실패해도_나머지_지역은_채운다() {
        YearMonth baseYm = YearMonth.of(2099, 10);
        AtomicInteger calls = new AtomicInteger();
        tourApiClient.respond(() -> {
            if (calls.getAndIncrement() == 0) {
                throw TourApiException.serviceUnavailable();
            }
            return result(poi("C-11", "NA", "뒤 지역의 장소", "http://img/11.jpg"));
        });

        // 예외가 밖으로 새면 이 호출 자체가 터진다.
        int filled = refreshService.refresh(baseYm);

        assertTrue(filled > 0, "첫 지역이 실패하자 뒤 지역까지 안 채웠다");
    }

    /** 분류가 안 서는 장소는 담지 않는다 — 어느 칩을 눌러도 나오는 장소가 생기면 안 된다. */
    @Test
    void 분류가_안_서는_장소는_담지_않는다() {
        YearMonth baseYm = YearMonth.of(2099, 11);
        long regionId = anyRegionId();
        tourApiClient.respond(() -> result(
                poi("C-9", null, "대분류 없음", "http://img/9.jpg"),
                poi("C-10", "ZZ", "모르는 대분류", "http://img/10.jpg")));

        refreshService.refresh(baseYm);

        List<RegionPoi> shown = regionPoiRepository.findShowable(regionId, 10);
        assertTrue(shown.stream().noneMatch(p -> "C-9".equals(p.getContentId())), "대분류 없는 장소가 담겼다");
        assertTrue(shown.stream().noneMatch(p -> "C-10".equals(p.getContentId())), "모르는 대분류가 담겼다");
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        TourApiClient stubTourApiClient() {
            return new StubTourApiClient();
        }
    }

    private long anyRegionId() {
        List<Region> regions = regionRepository.findAll();
        assertFalse(regions.isEmpty(), "지역 마스터가 비어 있어 이 테스트가 성립하지 않는다");
        return regions.get(0).getId();
    }

    private static TourPoiResult result(TourPoi... items) {
        return new TourPoiResult(List.of(items), items.length);
    }

    private static TourPoi poi(String contentId, String lclsSystm1, String title, String image) {
        return new TourPoi(contentId, 12, lclsSystm1, title, "주소", 37.5, 127.0, image, "051-000-0000", null);
    }
}
