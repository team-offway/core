package com.offway.core.trip.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.GalleryPhoto;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.gallery.GalleryImageVerifier;
import com.offway.core.trip.infrastructure.gallery.GalleryPhotoClient;
import com.offway.core.trip.infrastructure.gallery.StubGalleryImageVerifier;
import com.offway.core.trip.infrastructure.gallery.StubGalleryPhotoClient;
import com.offway.core.trip.infrastructure.gallery.dto.GalleryPhotoItem;
import com.offway.core.trip.repository.GalleryPhotoRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 관광사진 갤러리 적재(#196) — 페이지 순회 · 지역 매핑 · 죽은 이미지 제외.
 *
 * <p>여기서 지키는 것은 <b>"카드에 깨진 이미지가 나가지 않는가"</b> 다. 갤러리가 주는 URL 중 19.3%가 404 라
 * (실측 2026-08-09) 확인 없이 적재하면 지역 15곳이 깨진 채로 나갔다.
 */
@SpringBootTest
class GalleryPhotoRefreshIntegrationTest {

    @Autowired
    private GalleryPhotoRefreshService refreshService;

    @Autowired
    private StubGalleryPhotoClient galleryPhotoClient;

    @Autowired
    private StubGalleryImageVerifier galleryImageVerifier;

    @Autowired
    private GalleryPhotoRepository galleryPhotoRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private com.offway.core.common.batch.repository.BatchRunRepository batchRunRepository;

    /** 이 배치가 "이번 주에는 아직 안 돌았다" 인 상태로 만든다 — 실행 기록은 커밋되므로 각 테스트가 직접. */
    private void notRunThisWeek() {
        batchRunRepository.markStarted(
                GalleryPhotoRefreshService.BATCH_NAME, java.time.LocalDateTime.now().minusDays(30));
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        GalleryPhotoClient stubGalleryPhotoClient() {
            return new StubGalleryPhotoClient();
        }

        @Bean
        @Primary
        GalleryImageVerifier stubGalleryImageVerifier() {
            return new StubGalleryImageVerifier();
        }
    }

    private Region anyRegion() {
        return regionRepository.findAll().getFirst();
    }

    private static GalleryPhotoItem item(String id, String title, String url, String location) {
        return new GalleryPhotoItem(id, title, url, "202405", location, "촬영자", title + " 키워드");
    }

    /** 한 페이지만 주고 끝낸다 — 페이지 크기(1,000)보다 적으면 마지막 페이지다. */
    private void respondOnce(List<GalleryPhotoItem> items) {
        galleryPhotoClient.respond((pageNo, rows) -> pageNo == 1 ? items : List.of());
    }

    @Test
    void 촬영_위치로_지역을_붙여_적재한다() {
        Region region = anyRegion();
        String location = region.getSido() + " " + region.getSigungu();
        galleryImageVerifier.allAlive();
        respondOnce(List.of(item("c1", "어느명소", "http://img/1.jpg", location)));

        refreshService.refresh();

        List<GalleryPhoto> stored = galleryPhotoRepository.findByRegionId(region.getId());
        assertEquals(1, stored.size());
        assertEquals("어느명소", stored.getFirst().getTitle());
    }

    @Test
    void 죽은_이미지는_저장하지_않는다() {
        // 갤러리가 주는 URL 중 19.3%가 404 다. 그대로 두면 카드에 깨진 이미지가 나간다.
        Region region = anyRegion();
        String location = region.getSido() + " " + region.getSigungu();
        respondOnce(List.of(
                item("c1", "죽은사진", "http://img/dead.jpg", location),
                item("c2", "살아있는사진", "http://img/alive.jpg", location)));
        galleryImageVerifier.respond(urls -> Set.of("http://img/alive.jpg"));

        refreshService.refresh();

        List<GalleryPhoto> stored = galleryPhotoRepository.findByRegionId(region.getId());
        assertEquals(1, stored.size());
        assertEquals("살아있는사진", stored.getFirst().getTitle());
    }

    @Test
    void 지역이_안_붙은_사진은_생존_확인_없이_남긴다() {
        // 대표 사진 후보가 아니라 확인할 이유가 없다. 정규화 규칙이 바뀌면 다시 매길 원본이라 버리지도 않는다.
        respondOnce(List.of(item("c1", "어딘가", "http://img/x.jpg", "신승반점")));
        galleryImageVerifier.respond(urls -> {
            throw new AssertionError("지역이 없는 사진까지 생존 확인을 했다 — 남의 서버에 불필요한 요청이 간다");
        });

        refreshService.refresh();

        assertEquals(1, galleryPhotoRepository.count());
        assertEquals(0, galleryPhotoRepository.countWithRegion());
    }

    @Test
    void 생존_확인이_통째로_실패하면_확인_없이_적재한다() {
        // 네트워크 단절 등으로 한 건도 확인 못 한 상황. 전부 버리면 전 지역 대표 사진이 사라진다 —
        // 깨진 이미지 몇 장이 전 지역 공백보다 낫다.
        Region region = anyRegion();
        String location = region.getSido() + " " + region.getSigungu();
        respondOnce(List.of(item("c1", "어느명소", "http://img/1.jpg", location)));
        galleryImageVerifier.respond(urls -> Set.of());

        refreshService.refresh();

        assertFalse(galleryPhotoRepository.findByRegionId(region.getId()).isEmpty());
    }

    @Test
    void 조회가_실패하면_이전_적재를_유지한다() {
        Region region = anyRegion();
        String location = region.getSido() + " " + region.getSigungu();
        galleryImageVerifier.allAlive();
        respondOnce(List.of(item("c1", "이전사진", "http://img/old.jpg", location)));
        refreshService.refresh();

        galleryPhotoClient.respond((pageNo, rows) -> {
            throw TourApiException.lookupFailed(new RuntimeException("upstream down"));
        });
        refreshService.refresh();

        assertEquals("이전사진", galleryPhotoRepository.findByRegionId(region.getId()).getFirst().getTitle());
    }

    @Test
    void 페이지_상한에_닿으면_부분_수집을_버린다() {
        // 상한은 totalCount 가 잘못 커지는 비정상 상황 대비인데, 하필 그때 부분 수집으로 전량을 덮으면
        // 못 읽은 페이지의 지역들이 조용히 사라진다. 적재 자체를 건너뛰어 이전 값을 남긴다.
        Region region = anyRegion();
        String location = region.getSido() + " " + region.getSigungu();
        galleryImageVerifier.allAlive();
        respondOnce(List.of(item("c1", "이전사진", "http://img/old.jpg", location)));
        refreshService.refresh();

        // 페이지가 끝나지 않고 계속 가득 찬 응답을 준다 — 상한까지 돌게 된다.
        galleryPhotoClient.respond((pageNo, rows) ->
                java.util.stream.IntStream.range(0, rows)
                        .mapToObj(i -> item("p" + pageNo + "-" + i, "새사진", "http://img/new.jpg", location))
                        .toList());
        refreshService.refresh();

        assertEquals("이전사진", galleryPhotoRepository.findByRegionId(region.getId()).getFirst().getTitle());
    }

    @Test
    void 컬럼_길이를_넘는_항목은_건너뛴다() {
        // 적재가 한 트랜잭션이라 한 건이 컬럼을 넘으면 그 주 적재가 통째로 실패한다.
        Region region = anyRegion();
        String location = region.getSido() + " " + region.getSigungu();
        galleryImageVerifier.allAlive();
        respondOnce(List.of(
                item("c1", "정상", "http://img/ok.jpg", location),
                item("c2", "긴URL", "http://img/" + "x".repeat(500) + ".jpg", location)));

        refreshService.refresh();

        assertEquals(1, galleryPhotoRepository.count());
    }

    @Test
    void 빈_결과로_이전_적재를_덮지_않는다() {
        Region region = anyRegion();
        String location = region.getSido() + " " + region.getSigungu();
        galleryImageVerifier.allAlive();
        respondOnce(List.of(item("c1", "이전사진", "http://img/old.jpg", location)));
        refreshService.refresh();

        galleryPhotoClient.respond((pageNo, rows) -> List.of());
        refreshService.refresh();

        assertTrue(galleryPhotoRepository.count() > 0, "빈 결과로 덮으면 전 지역 대표 사진이 사라진다");
    }

    @Test
    void 이번_주에_이미_돌았으면_외부를_부르지_않는다() {
        // fixedDelay 는 프로세스가 사는 동안의 간격이라, 재배포하면 주기가 처음부터 다시 센다.
        // 주 1회로 잡아 뒀는데도 부팅마다 7회 조회 + 이미지 1,790건 검증이 돌고 있었다.
        notRunThisWeek();
        galleryImageVerifier.allAlive();
        respondOnce(List.of(item("c1", "어느명소", "http://img/1.jpg", anyRegion().getSido())));
        refreshService.refreshIfStale();

        galleryPhotoClient.respond((pageNo, rows) -> {
            throw new AssertionError("이번 주에 이미 돌았는데 갤러리를 불렀다");
        });
        refreshService.refreshIfStale();
    }
}
