package com.offway.core.trip.service;

import com.offway.core.common.cache.ExternalDataCache;
import com.offway.core.common.cache.ExternalDataCache.Loaded;
import com.offway.core.common.cache.ExternalDataCache.StalePolicy;
import com.offway.core.trip.domain.HeritagePlace;
import com.offway.core.trip.domain.LicensedPlace;
import com.offway.core.trip.domain.MapSearchLink;
import com.offway.core.trip.domain.PoiContentType;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.domain.PoiIntro;
import com.offway.core.trip.infrastructure.tour.dto.TourIntro;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiDetail;
import com.offway.core.trip.repository.HeritagePlaceRepository;
import com.offway.core.trip.repository.LicensedPlaceRepository;
import com.offway.core.trip.service.dto.PoiDetail;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 장소 상세 조회(F4 표시요소·course-logic ③) — 공통상세(detailCommon2)에 소개정보(detailIntro2)의 운영시간·휴무일을
 * 합친다. 코스 타임라인에서 장소를 누르면 상세를 보여준다.
 *
 * <p>TourAPI 는 read-timeout 이 길어 트랜잭션 밖에서 호출한다(persistence-convention). 장소가 없으면
 * {@link TourApiException#poiNotFound()}(404).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PoiDetailService {

    /** TourAPI 콘텐츠가 아님을 뜻하는 타입 — 인허가·국가유산이 함께 쓴다. 실제 contentTypeId 는 12·32·39 처럼 모두 양수다. */
    private static final int NON_TOUR_CONTENT_TYPE = 0;

    /**
     * 성공 캐시 TTL — 상세는 <b>느리게 변하는 값</b>이다(주소·개요·운영시간·휴무일).
     *
     * <p>실측(2026-08-12, 표본 30건) p50 109ms · p95 151ms 로 평시엔 빠르지만, 값이 빠른 것과 자주
     * 부를 이유가 있는 것은 다르다. 같은 장소를 누를 때마다 외부를 치면 일일 한도를 그만큼 태우고,
     * 외부가 멈춘 순간을 만날 확률도 호출 수에 비례해 오른다.
     */
    private static final Duration CACHE_TTL = Duration.ofHours(6);

    /**
     * 조회 실패 TTL — 짧게 둬 재시도를 유도한다.
     *
     * <p>실패를 성공 TTL 로 누르면 그 장소가 6시간 동안 죽는다. 반대로 아예 안 누르면 외부가 느린 동안
     * 모든 요청이 각자 8초를 기다린다(호출 하나의 상한 6초 + 429 재시도). 1분이 그 사이다.
     */
    private static final Duration FAILURE_CACHE_TTL = Duration.ofMinutes(1);

    /**
     * 없는 콘텐츠 TTL — 실패보다는 길게, 성공보다는 짧게.
     *
     * <p>"없다" 는 안정된 답이라 매번 물을 이유가 없다. 다만 우리가 코스에 실어 보낸 식별자가 404 라면
     * 그건 우리 쪽 버그 신호라, 오래 굳혀 두면 고친 뒤에도 한동안 404 가 나간다.
     */
    private static final Duration NOT_FOUND_CACHE_TTL = Duration.ofMinutes(10);

    /**
     * 캐시 엔트리 상한 — <b>키 공간은 TourAPI contentId 다</b>.
     *
     * <p>89곳 전체 콘텐츠는 실측 기준 지역당 100건 안팎(최대 평창군 446건)이라 만 단위까지 갈 수 있다.
     * 다만 실제로 눌리는 것은 코스에 실린 장소뿐이라 훨씬 적다 — 코스 하나가 슬롯 20개 남짓이다.
     * 상한을 두는 이유는 <b>TTL 이 엔트리를 지우지 않기 때문</b>이다(성능 규약).
     */
    private static final int MAX_CACHED_DETAILS = 2_000;

    /**
     * 빈 키에 동시 요청이 몰렸을 때 첫 적재를 기다릴 상한.
     *
     * <p>loader 는 외부를 <b>두 번</b> 부른다(공통상세 → 소개정보). 호출 하나의 상한이 8초라 최악 16초인데,
     * 그만큼 기다리게 하면 사용자는 이미 떠났다. 평시 왕복이 220ms 라 8초면 정상 경로에 닿지 않는다 —
     * 느려졌을 때만 끊는 안전망이다.
     */
    private static final Duration FIRST_LOAD_WAIT = Duration.ofSeconds(8);

    private final TourApiClient tourApiClient;
    private final CatchphraseProvider catchphraseProvider;
    private final LicensedPlaceRepository licensedPlaceRepository;
    private final HeritagePlaceRepository heritagePlaceRepository;

    /**
     * 관광 API 상세 캐시 — 인허가·국가유산은 우리 DB 라 캐시하지 않는다.
     *
     * <p><b>stale 을 허용한다.</b> 상세는 느리게 변하므로 6시간 전 값이 502 보다 낫다. 실제로 그 차이가
     * 났다 — 외부가 6초 안에 답하지 않은 순간(2026-08-11 04:14) 사용자는 화면을 통째로 못 봤는데,
     * 캐시가 있었다면 직전 값이 나갔다.
     */
    private final ExternalDataCache<String, CachedDetail> detailCache =
            new ExternalDataCache<>(MAX_CACHED_DETAILS, FIRST_LOAD_WAIT);

    /**
     * 캐시에 담는 조회 결과.
     *
     * <p>{@code detail} 만 담으면 "없는 콘텐츠(404)" 와 "조회 실패(502)" 가 둘 다 null 이 돼 구분되지
     * 않는다. 클라이언트 계약이 갈리는 자리라 상태를 함께 담는다.
     */
    private record CachedDetail(PoiDetail detail, boolean lookupFailed) {

        static CachedDetail found(PoiDetail detail) {
            return new CachedDetail(detail, false);
        }

        static CachedDetail notFound() {
            return new CachedDetail(null, false);
        }

        static CachedDetail failed() {
            return new CachedDetail(null, true);
        }
    }

    public PoiDetail detail(String contentId) {
        // 코스 응답에는 두 출처의 식별자가 섞여 나간다. 인허가 장소를 TourAPI 에 물으면 없는 콘텐츠라
        // 404 가 떨어지므로, 우리 식별자는 우리 DB 가 답한다(#144). 사진·소개는 없지만 상호·주소·전화는 있다.
        Optional<Long> licensedId = LicensedPlace.parsePublicId(contentId);
        if (licensedId.isPresent()) {
            return licensedDetail(licensedId.get());
        }
        Optional<Long> heritageId = HeritagePlace.parsePublicId(contentId);
        if (heritageId.isPresent()) {
            return heritageDetail(heritageId.get());
        }

        return tourDetail(contentId);
    }

    /**
     * 관광 API 상세 — 캐시를 거친다.
     *
     * <p>캐시가 없던 때는 같은 장소를 누를 때마다 외부를 두 번씩 쳤다. 운영 로그에서 <b>같은 contentId 가
     * 40초 안에 세 번</b> 조회되는 것을 봤는데, 호출이 세 배면 외부가 멈춘 순간을 만날 확률도 세 배다.
     */
    private PoiDetail tourDetail(String contentId) {
        CachedDetail cached =
                detailCache.get(contentId, this::loadDetail, CachedDetail.failed(), StalePolicy.ALLOW_STALE);
        if (cached.lookupFailed()) {
            // 캐시가 잡아 둔 실패다. 원인은 loader 안에서 이미 로그로 남았다.
            throw TourApiException.lookupFailed(new IllegalStateException("관광 API 상세 조회 실패(캐시된 결과)"));
        }
        if (cached.detail() == null) {
            throw TourApiException.poiNotFound();
        }
        return cached.detail();
    }

    /**
     * 캐시 loader — <b>외부 예외를 스스로 잡는다</b>(캐시 프리미티브의 계약).
     *
     * <p>실패했는데 직전 성공값이 있으면 그걸 그대로 돌려준다. 상세는 느리게 변하므로 6시간 전 값이
     * 502 보다 낫다. 다만 TTL 은 짧게 줘, 외부가 돌아오면 곧 다시 받아온다.
     */
    private Loaded<CachedDetail> loadDetail(String contentId, CachedDetail stale) {
        try {
            Optional<TourPoiDetail> found = tourApiClient.findDetail(contentId);
            if (found.isEmpty()) {
                return new Loaded<>(CachedDetail.notFound(), NOT_FOUND_CACHE_TTL);
            }
            return new Loaded<>(CachedDetail.found(toPoiDetail(contentId, found.get())), CACHE_TTL);
        } catch (RuntimeException e) {
            if (stale != null && stale.detail() != null) {
                log.warn("관광 API 상세 조회 실패 — 직전 값으로 내려보냅니다 contentId={} cause={}",
                        contentId, e.getClass().getSimpleName());
                return new Loaded<>(stale, FAILURE_CACHE_TTL);
            }
            // degrade 를 조용히 넘기지 않는다 — 폴백이 정상처럼 보이면 장애를 아무도 모른다.
            log.warn("관광 API 상세 조회 실패 — 내려보낼 직전 값이 없습니다 contentId={} cause={}",
                    contentId, e.getClass().getSimpleName());
            return new Loaded<>(CachedDetail.failed(), FAILURE_CACHE_TTL);
        }
    }

    /** 외부 응답을 도메인으로 옮긴다 — 상위 레이어(서비스 dto·응답 dto)가 어댑터 DTO 를 들지 않게. */
    private PoiDetail toPoiDetail(String contentId, TourPoiDetail detail) {
        PoiIntro intro = detail.contentTypeId() == null
                ? null
                : tourApiClient.findIntro(contentId, detail.contentTypeId())
                        .map(TourIntro::toPoiIntro)
                        .orElse(null);

        return new PoiDetail(
                detail.contentId(),
                detail.contentTypeId(),
                PoiContentType.labelOf(detail.contentTypeId()),
                detail.title(),
                detail.address(),
                detail.tel(),
                detail.lat(),
                detail.lng(),
                detail.imageUrl(),
                detail.overview(),
                intro,
                null, // 관광 API 콘텐츠는 사진·소개·운영시간이 이미 있어 지도로 넘길 이유가 없다
                catchphraseProvider.forContentId(contentId).orElse(null));
    }

    /** 강제 갱신·통합 테스트 격리용. 공유 컨텍스트에서 앞 테스트의 캐시가 뒤 테스트를 통과시키지 않게. */
    public void evictCache() {
        detailCache.evictAll();
    }

    /**
     * 국가유산의 상세(#160) — 인허가와 달리 <b>사진과 설명이 있다</b>.
     *
     * <p>여기 분기가 없으면 {@code HER-} 식별자가 TourAPI 로 넘어가 404 가 난다. 코스에는 나가는데 누르면
     * 없다고 하는 셈이라, 후보로 쓰기 시작한 순간 함께 있어야 하는 경로다.
     *
     * <p>운영시간·휴무일은 국가유산청이 주지 않는다. 없는 것을 지어내지 않고 비운다.
     */
    private PoiDetail heritageDetail(long id) {
        HeritagePlace heritage = heritagePlaceRepository.findById(id).orElseThrow(TourApiException::poiNotFound);
        // 보조정보 없음을 팩토리로 말한다 — 생성자에 null 을 줄줄이 넘기면 필드가 늘 때마다 여기가 깨진다.
        // 실제로 그렇게 깨졌다: #235 가 운영시간·휴무일을 intro 하나로 접었는데 이 호출은 둘을 따로 넘기고
        // 있어서, 텍스트 충돌 없이 머지된 뒤 컴파일에서 터졌다.
        return PoiDetail.withoutIntro(
                heritage.publicId(),
                NON_TOUR_CONTENT_TYPE,
                // 종목이 곧 뱃지다 — `국보`·`보물`·`사적`·`천연기념물`. 대분류(유적건조물)보다 사용자에게 익다.
                heritage.getKind(),
                heritage.getName(),
                heritage.getAddress(),
                null, // 전화 — 국가유산청이 주지 않는다
                heritage.getLat(),
                heritage.getLng(),
                heritage.getImageUrl(),
                heritage.getDescription(),
                // 국가유산도 운영시간·전화가 없다. 사진·설명은 있지만 "언제 여나" 는 지도가 답한다.
                MapSearchLink.of(heritage.getName(), heritage.getAddress()).orElse(null));
    }

    /** 인허가 장소의 상세 — 우리가 가진 것만 채우고 나머지는 비운다. 없는 것을 지어내지 않는다. */
    private PoiDetail licensedDetail(long id) {
        LicensedPlace place = licensedPlaceRepository.findById(id).orElseThrow(TourApiException::poiNotFound);
        return PoiDetail.withoutIntro(
                place.publicId(),
                NON_TOUR_CONTENT_TYPE,
                // 인허가는 업종 분류가 곧 뱃지다 — `한옥체험`·`전통사찰`·`한식`.
                place.getCategory().label(),
                place.getName(),
                place.getAddress(),
                place.getTel(),
                place.getLat(),
                place.getLng(),
                null, // 사진
                null, // 소개글
                MapSearchLink.of(place.getName(), place.getAddress()).orElse(null));
    }
}
