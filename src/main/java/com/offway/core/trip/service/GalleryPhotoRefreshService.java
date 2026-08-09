package com.offway.core.trip.service;

import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.GalleryPhoto;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.gallery.GalleryPhotoClient;
import com.offway.core.trip.infrastructure.gallery.dto.GalleryPhotoItem;
import com.offway.core.trip.repository.GalleryPhotoRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 관광사진 갤러리를 받아 DB 에 적재하고 지역을 붙인다(#196).
 *
 * <p><b>요청 경로에서 부르지 않는다.</b> 대표 사진을 요청마다 외부에서 찾으면 일일 한도가 금방 마르고,
 * 인메모리로 들고 있으면 배포마다 다시 긁는다(#193 과 같은 원칙).
 *
 * <p>전량이 6,118건뿐이라 통째로 갈아끼운다 — 원본에서 내려간 사진이 남으면 죽은 URL 이 대표 사진으로 걸린다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GalleryPhotoRefreshService {

    /** 부팅 후 첫 적재까지 지연 — 기동·헬스체크를 방해하지 않게. */
    private static final String INITIAL_DELAY = "PT45S";

    /**
     * 재적재 주기.
     *
     * <p>사진은 월 단위로 조금씩 늘 뿐이라 자주 물을 이유가 없다. 한 번에 7회 호출이므로 주 1회면 일일
     * 한도(#193)에 거의 영향이 없다.
     */
    private static final String REFRESH_INTERVAL = "P7D";

    /**
     * 페이지 크기 — 실측상 1,000 이 그대로 받아들여져 전량이 7페이지로 끝난다.
     *
     * <p>더 키우지 않는 이유: 페이지당 554KB 라 {@code externalWebClient} 의 {@code maxInMemorySize}(2MB)
     * 안에 들어야 한다.
     */
    private static final int PAGE_SIZE = 1_000;

    /** 페이지 폭주 안전장치 — totalCount 가 잘못 커도 무한 루프에 빠지지 않게. */
    private static final int MAX_PAGES = 20;

    private final GalleryPhotoClient galleryPhotoClient;
    private final GalleryPhotoRepository galleryPhotoRepository;
    private final RegionRepository regionRepository;

    @Scheduled(initialDelayString = INITIAL_DELAY, fixedDelayString = REFRESH_INTERVAL)
    public void refresh() {
        List<GalleryPhotoItem> items;
        try {
            items = fetchAll();
        } catch (TourApiException e) {
            // 사진은 있으면 좋은 값이라 조회 실패로 홈·추천을 막지 않는다. 이전 적재가 그대로 쓰인다.
            log.warn("관광사진 갤러리 적재 실패 — 이전 적재를 유지합니다 cause={}", e.getClass().getSimpleName());
            return;
        }
        if (items.isEmpty()) {
            // 빈 결과로 덮으면 전 지역 대표 사진이 통째로 사라진다. 이전 값이 낫다.
            log.warn("관광사진 갤러리 결과가 비어 적재를 건너뜁니다 — 이전 적재를 유지합니다(저장={}건)",
                    galleryPhotoRepository.count());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<GalleryPhoto> photos = items.stream().map(item -> item.toEntity(now)).toList();
        assignRegions(photos);
        galleryPhotoRepository.replaceAll(photos);

        long withRegion = photos.stream().filter(p -> p.getRegionId() != null).count();
        log.info("관광사진 갤러리 적재 완료 사진={}건 — 우리 지역에 붙은 것 {}건", photos.size(), withRegion);
    }

    /**
     * 전 페이지를 모은다. 마지막 페이지는 결과가 비거나 총건수에 닿아 끝난다.
     *
     * <p>중간에 실패하면 <b>모은 것을 버린다</b> — 부분 적재로 덮으면 뒷페이지 지역의 사진이 통째로 사라지는데,
     * 그건 갱신을 포기하는 것보다 나쁘다(조용히 틀린다).
     */
    private List<GalleryPhotoItem> fetchAll() {
        List<GalleryPhotoItem> all = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            List<GalleryPhotoItem> pageItems = galleryPhotoClient.findPage(page, PAGE_SIZE);
            if (pageItems.isEmpty()) {
                return all;
            }
            all.addAll(pageItems);
            if (pageItems.size() < PAGE_SIZE) {
                return all;
            }
        }
        log.warn("관광사진 갤러리 페이지 상한({}) 도달 — 수집 {}건", MAX_PAGES, all.size());
        return all;
    }

    /** 촬영 위치 원문을 우리 89곳에 붙인다. 못 붙인 사진은 지역 없이 남아 대표 사진 후보에서 빠진다. */
    private void assignRegions(List<GalleryPhoto> photos) {
        List<Region> regions = regionRepository.findAll();
        if (regions.isEmpty()) {
            return;
        }
        GalleryRegionMatcher matcher = GalleryRegionMatcher.from(regions);
        for (GalleryPhoto photo : photos) {
            Optional<Long> regionId = matcher.match(photo.getPhotographyLocation());
            regionId.ifPresent(photo::assignRegion);
        }
    }
}
