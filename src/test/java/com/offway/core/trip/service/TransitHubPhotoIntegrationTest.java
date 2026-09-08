package com.offway.core.trip.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.trip.infrastructure.gallery.GalleryPhotoClient;
import com.offway.core.trip.infrastructure.gallery.StubGalleryPhotoClient;
import com.offway.core.trip.domain.TransitHubPhoto;
import com.offway.core.trip.infrastructure.gallery.dto.GalleryPhotoItem;
import com.offway.core.trip.infrastructure.gallery.dto.GallerySearch;
import com.offway.core.trip.repository.TransitHubPhotoRepository;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

/**
 * 역·터미널·항구 칸의 사진(#450) — 관광사진갤러리에서 미리 받아 둔다.
 *
 * <p>대상은 인구감소지역 89곳이 쓰는 도착 지점뿐이다. 코스가 <b>도착 지역부터</b> 보여주므로 그 집합이
 * 곧 필요한 전부고, 시드가 정한 유한 집합이라 미리 받을 수 있다.
 */
@SpringBootTest
// 테스트마다 이 테이블을 통째로 채우므로 롤백으로 격리한다 — 앞 회차가 전량을 채워 두면 다음 회차는
// "최근에 받았다" 로 아무것도 안 하게 되어, 검증하려는 분기에 도달하지 못한다(테스트 규약의 그 방식).
@Transactional
class TransitHubPhotoIntegrationTest {

    /** 갤러리가 사진을 준 척 — 지점 이름을 그대로 되돌려 어느 키워드로 물었는지 확인한다. */
    private static GalleryPhotoItem photoFor(String keyword) {
        return new GalleryPhotoItem(
                "gal-" + keyword, keyword + " 전경", "https://tong.visitkorea.or.kr/" + keyword + ".jpg",
                "202509", "강원특별자치도", "촬영자", keyword);
    }

    @Autowired
    private TransitHubPhotoRefreshService refreshService;

    @Autowired
    private TransitHubPhotoProvider photoProvider;

    @Autowired
    private TransitHubPhotoRepository transitHubPhotoRepository;

    @Autowired
    private StubGalleryPhotoClient galleryPhotoClient;

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        GalleryPhotoClient stubGalleryPhotoClient() {
            return new StubGalleryPhotoClient();
        }
    }

    @Test
    void 도착_지점의_사진을_미리_받아_둔다() {
        galleryPhotoClient.respondToSearch(keyword -> List.of(photoFor(keyword)));

        refreshService.refresh();

        // 89곳의 열차역·고속·시외·항구를 이름으로 모은 집합이다. 정확한 수는 시드가 정하므로 하한만 본다.
        assertTrue(transitHubPhotoRepository.findAll().size() >= 100,
                "받아 둔 지점이 너무 적습니다 — 도착 지점 해석을 확인하세요: "
                        + transitHubPhotoRepository.findAll().size());
    }

    /**
     * <b>못 찾은 것도 적는다.</b> 행이 없는 것은 "아직 안 물어봄" 이고 사진이 비어 있는 것은 "물어봤는데
     * 없다" 다. 뭉치면 매 회차가 같은 지점을 다시 묻는다.
     */
    @Test
    void 사진이_없는_지점도_결과를_남긴다() {
        galleryPhotoClient.respondToSearch(keyword -> List.of());

        refreshService.refresh();

        assertTrue(transitHubPhotoRepository.findAll().stream().anyMatch(photo -> photo.getImageUrl() == null),
                "사진이 없는 지점의 행이 없습니다 — 배치가 매 회차 같은 곳을 다시 묻게 됩니다");
    }

    /** 최근에 받은 지점은 다시 안 묻는다 — 매주 도는 배치가 같은 것을 계속 물으면 한도가 샌다. */
    @Test
    void 최근에_받은_지점은_다시_묻지_않는다() {
        galleryPhotoClient.respondToSearch(keyword -> List.of(photoFor(keyword)));
        refreshService.refresh();
        int after = transitHubPhotoRepository.findAll().size();

        galleryPhotoClient.respondToSearch(keyword -> {
            throw new AssertionError("최근에 받은 지점을 다시 물었습니다: " + keyword);
        });
        refreshService.refresh();

        assertEquals(after, transitHubPhotoRepository.findAll().size());
    }

    /** 읽기 쪽은 <b>DB 만 본다</b> — 코스 응답이 갤러리 응답시간을 뒤집어쓰면 안 된다. */
    @Test
    void 읽기는_외부를_부르지_않는다() {
        galleryPhotoClient.respondToSearch(keyword -> List.of(photoFor(keyword)));
        refreshService.refresh();
        Set<String> names = transitHubPhotoRepository.findAll().stream()
                .filter(photo -> photo.getImageUrl() != null)
                .map(com.offway.core.trip.domain.TransitHubPhoto::getHubName)
                .limit(3)
                .collect(java.util.stream.Collectors.toSet());

        galleryPhotoClient.respondToSearch(keyword -> {
            throw new AssertionError("읽기 경로가 갤러리를 불렀습니다: " + keyword);
        });
        Map<String, String> urls = photoProvider.photoUrls(names);

        assertEquals(names.size(), urls.size(), "받아 둔 사진을 못 읽었습니다");
    }

    /**
     * 지점명으로 못 찾으면 <b>더 일반적인 말로 다시 묻는다</b> — 마지막은 그 지역명이다.
     *
     * <p>지점명 그대로만 물으면 158종 중 108종(68%)만 나온다(실측). 못 찾는 것들은 대부분 이름이
     * 검색어로 안 맞는 경우다 — `광주(유·스퀘어)` 의 괄호, `부산_영도` 의 밑줄, `고양종합` 의 시설 접미어.
     * 그래도 안 나오는 `점촌`·`탄현` 은 지역명(문경·파주)으로 물으면 나온다.
     */
    @Test
    void 지점명으로_못_찾으면_더_일반적인_말로_다시_묻는다() {
        // **아무것도 안 주는 갤러리로 잰다.** 사진을 주면 첫 검색어에서 멈춰 대체어를 안 쓰고,
        // 짧은 지점명이 많아(정선·완도·양양…) "짧은 말을 물었나" 로는 갈리지 않는다.
        List<String> asked = new java.util.ArrayList<>();
        galleryPhotoClient.respondToSearch(keyword -> {
            asked.add(keyword);
            return List.of();
        });

        refreshService.refresh();

        long hubs = transitHubPhotoRepository.findAll().size();
        assertTrue(asked.size() > hubs,
                "지점마다 검색어를 하나씩만 썼습니다 — 대체어를 안 쓴 것입니다: 검색 %d회 / 지점 %d곳"
                        .formatted(asked.size(), hubs));
        // 마지막 폴백은 지역명이다 — 지점명과 다른 말이 섞여야 한다.
        assertTrue(asked.stream().distinct().count() > hubs,
                "서로 다른 검색어가 지점 수보다 많아야 합니다");
    }

    /** 안 받아 둔 지점은 결과에서 빠진다 — 없는 것을 지어내지 않는다. */
    @Test
    void 안_받아_둔_지점은_결과에_없다() {
        Map<String, String> urls = photoProvider.photoUrls(Set.of("있을 리 없는 지점 이름"));

        assertTrue(urls.isEmpty());
    }

    /**
     * <b>못 물어본 것을 "없음" 으로 적지 않는다</b>(#535).
     *
     * <p>운영에서 이 배치가 4주 동안 갤러리를 <b>한 번도 부르지 않았는데</b> 155곳 전부가 3일마다
     * "물어봤는데 없음" 으로 다시 기록됐다. 조회 실패와 실제 미검색이 둘 다 빈 목록이라 갈리지
     * 않았기 때문이다 — 묻지도 않고 없다고 적는 셈이었다.
     *
     * <p>행을 안 남겨야 다음 회차가 다시 묻는다. 없음으로 적으면 그 상태가 그대로 굳는다.
     */
    @Test
    void 못_물어본_지점은_없음으로_적지_않는다() {
        galleryPhotoClient.failSearch();

        refreshService.refresh();

        assertTrue(transitHubPhotoRepository.findAll().isEmpty(),
                "묻지도 못했는데 결과를 적었다 — 그 상태가 그대로 굳는다");
    }

    /**
     * <b>물어봤는데 없는 것은 적는다</b>(#535).
     *
     * <p>위와 갈라야 하는 이유가 이것이다. 이쪽은 남겨 둬야 매 회차가 같은 지점을 다시 묻지 않는다.
     */
    @Test
    void 물어봤는데_없으면_결과를_남긴다() {
        galleryPhotoClient.respondToSearch(keyword -> List.of());

        refreshService.refresh();

        assertTrue(transitHubPhotoRepository.findAll().size() >= 100,
                "물어봤는데 없는 것까지 안 적으면 매 회차가 같은 지점을 다시 묻는다");
        assertTrue(transitHubPhotoRepository.findAll().stream()
                        .allMatch(photo -> photo.getImageUrl() == null),
                "사진이 없다고 했는데 주소가 붙었다");
    }

    /**
     * 못 물어본 지점이 섞여도 <b>물어본 지점의 결과는 남는다</b>(#535).
     *
     * <p>한 지점이 실패했다고 나머지를 버리면 갤러리가 잠깐 흔들릴 때마다 전량이 비어 버린다.
     *
     * <p><b>앞선 판마다 못 물어보게 만든다.</b> 처음에는 빈 목록을 돌려주는 것으로 이 시나리오를
     * 흉내 냈는데, 그건 "물어봤는데 없음" 이라 부분 실패가 아니었다 — 규칙을 되돌려도 그대로
     * 통과하는 가짜였다.
     *
     * <p>판정은 <b>저장된 행에 사진이 다 붙어 있는가</b>다. 못 물어본 지점까지 적으면 그 행들의
     * 주소가 비어 이 단언이 깨진다.
     */
    @Test
    void 일부만_못_물어봐도_나머지는_받아_둔다() {
        AtomicInteger asks = new AtomicInteger();
        galleryPhotoClient.respondToSearchWith(keyword -> asks.getAndIncrement() < 60
                ? GallerySearch.notAsked()
                : GallerySearch.asked(List.of(photoFor(keyword))));

        refreshService.refresh();

        List<TransitHubPhoto> stored = transitHubPhotoRepository.findAll();
        assertFalse(stored.isEmpty(), "앞선 실패 때문에 전량이 비었습니다");
        assertTrue(stored.stream().allMatch(photo -> photo.getImageUrl() != null),
                "못 물어본 지점까지 적었습니다 — 그 행은 주소가 빕니다");
    }
}
