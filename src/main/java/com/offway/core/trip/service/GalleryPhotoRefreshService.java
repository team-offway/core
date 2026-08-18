package com.offway.core.trip.service;

import com.offway.core.region.domain.Region;
import com.offway.core.common.batch.repository.BatchRunRepository;
import com.offway.core.common.external.Caller;
import com.offway.core.common.external.CallerContext;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.GalleryPhoto;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.gallery.GalleryImageVerifier;
import com.offway.core.trip.infrastructure.gallery.GalleryPhotoClient;
import com.offway.core.trip.infrastructure.gallery.dto.GalleryPhotoItem;
import com.offway.core.trip.repository.GalleryPhotoRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    /** 배치 식별자 — {@code batch_run} 의 키다. 바꾸면 "한 번도 안 돈 것" 이 되어 그 주에 한 번 더 돈다. */
    static final String BATCH_NAME = "gallery-photo-refresh";

    /** 이 배치가 태운 외부 호출에 붙는 이름(#285). 알림에 그대로 실리므로 사람이 읽는 말로 둔다. */
    private static final Caller CALLER = Caller.of("갤러리사진배치");

    /** 실행 간격 — 위 주기와 같은 값이어야 한다. 재부팅이 이 창 안이면 외부를 아예 안 부른다. */
    private static final Duration MIN_INTERVAL = Duration.ofDays(7);

    /** "지금" 판정은 KST — 저장 시각도 같은 기준이라야 한다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

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
    private final GalleryImageVerifier galleryImageVerifier;
    private final GalleryPhotoRepository galleryPhotoRepository;
    private final RegionRepository regionRepository;
    private final BatchRunRepository batchRunRepository;

    /**
     * 주 1회 — <b>그 주에 이미 돌았으면</b> 외부를 아예 안 부른다.
     *
     * <p>{@code fixedDelay} 는 <b>프로세스가 살아 있는 동안</b>의 간격이라, 재배포하면 주기가 처음부터
     * 다시 센다. 그래서 주 1회로 잡아 뒀는데도 부팅마다 7회 조회 + 이미지 1,790건 생존 확인이 돌았다.
     *
     * <p><b>결과가 아니라 실행을 기록한다.</b> 적재 결과로 판정하면 전부 실패한 회차에는 아무것도 안 써져
     * 다음 부팅이 또 돈다({@code HubAttractionRefreshService} 가 정확히 그랬다, #226).
     */
    @Scheduled(initialDelayString = INITIAL_DELAY, fixedDelayString = REFRESH_INTERVAL)
    public void refreshIfStale() {
        CallerContext.run(CALLER, () -> {
            LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
            if (batchRunRepository.hasRunSince(BATCH_NAME, now.minus(MIN_INTERVAL))) {
                log.info("관광사진 갤러리를 최근 {}에 이미 적재해 건너뜁니다", MIN_INTERVAL);
                return;
            }
            // 실패해도 남긴다 — 안 남기면 같은 주에 재부팅마다 다시 쏜다.
            batchRunRepository.markStarted(BATCH_NAME, now);
            refresh();
        });
    }

    /**
     * 전량을 다시 받아 적재한다 — <b>건너뛰기 없이</b>.
     *
     * <p>주기 판단은 {@link #refreshIfStale} 이 소유한다. 운영에서는 스케줄러만 이 경로를 탄다.
     */
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
        // 적재는 한 트랜잭션이라 컬럼을 넘는 한 건이 그 주 적재를 통째로 실패시킨다. 어댑터도 같은 검사를
        // 하지만 그건 실제 응답 경로뿐이고, 적재 실패를 막는 것은 적재하는 쪽의 책임이다.
        List<GalleryPhotoItem> storable = items.stream().filter(GalleryPhotoItem::isComplete).toList();
        if (storable.size() < items.size()) {
            log.warn("관광사진 갤러리 항목 {}건이 필수 값 누락·길이 초과로 제외됐습니다(수집 {}건)",
                    items.size() - storable.size(), items.size());
        }
        List<GalleryPhoto> photos = storable.stream().map(item -> item.toEntity(now)).toList();
        assignRegions(photos);
        List<GalleryPhoto> stored = withLiveImagesOnly(photos);
        galleryPhotoRepository.replaceAll(stored);

        long withRegion = stored.stream().filter(p -> p.getRegionId() != null).count();
        log.info("관광사진 갤러리 적재 완료 사진={}건 — 우리 지역에 붙은 것 {}건", stored.size(), withRegion);
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
        // 상한에 닿았다는 것은 끝을 못 봤다는 뜻이라 부분 수집이다. 그대로 돌려주면 호출자가 정상 결과로
        // 알고 전량 교체해, 못 읽은 페이지의 지역들이 조용히 사라진다. 빈 목록으로 실패를 알린다.
        log.warn("관광사진 갤러리 페이지 상한({}) 도달 — 부분 수집({}건)을 버립니다", MAX_PAGES, all.size());
        return List.of();
    }

    /**
     * <b>이미지가 실제로 살아 있는 것만</b> 남긴다 — 우리 지역에 붙은 사진에 한해서.
     *
     * <p>갤러리가 주는 URL 중 상당수가 죽어 있다(실측 2026-08-09: 우리 89곳에 붙은 1,790장 중 <b>345장이
     * 404</b>). 확인하지 않으면 지역 카드 15곳에 깨진 이미지가 그대로 나갔다.
     *
     * <p><b>지역이 붙은 것만 확인한다.</b> 전량 6,118장을 확인하면 남의 이미지 서버에 그만큼 요청이 가는데,
     * 우리가 쓰는 것은 지역이 붙은 것뿐이라 나머지는 확인할 이유가 없다.
     *
     * <p>지역이 없는 사진은 확인 없이 그대로 남긴다 — 대표 사진 후보가 아니고, 정규화 규칙이 바뀌면 다시
     * 매길 원본이기 때문이다.
     */
    private List<GalleryPhoto> withLiveImagesOnly(List<GalleryPhoto> photos) {
        List<String> urls = photos.stream()
                .filter(photo -> photo.getRegionId() != null)
                .map(GalleryPhoto::getImageUrl)
                .distinct()
                .toList();
        if (urls.isEmpty()) {
            return photos;
        }
        Set<String> alive = galleryImageVerifier.aliveUrls(urls);
        if (alive.isEmpty()) {
            // 확인 자체가 통째로 실패한 상황(네트워크 단절 등)이다. 전부 버리면 대표 사진이 다 사라지므로
            // 확인을 포기하고 그대로 둔다 — 깨진 이미지 몇 장이 전 지역 공백보다 낫다.
            log.warn("갤러리 이미지 생존 확인이 한 건도 성공하지 못했습니다 — 확인 없이 적재합니다(대상 {}건)",
                    urls.size());
            return photos;
        }
        return photos.stream()
                .filter(photo -> photo.getRegionId() == null || alive.contains(photo.getImageUrl()))
                .toList();
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
