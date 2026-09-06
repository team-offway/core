package com.offway.core.trip.service;

import com.offway.core.common.cache.ExternalDataCache;
import com.offway.core.common.cache.ExternalDataCache.Loaded;
import com.offway.core.common.cache.ExternalDataCache.StalePolicy;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCachePolicy;
import com.offway.core.common.logging.SensitiveParams;
import com.offway.core.itinerary.domain.SlotKind;
import com.offway.core.policy.service.PolicyService;
import com.offway.core.trip.domain.FestivalPlace;
import com.offway.core.trip.domain.HeritagePlace;
import com.offway.core.trip.domain.LicensedPlace;
import com.offway.core.trip.domain.MapSearchLink;
import com.offway.core.trip.domain.PoiContentType;
import com.offway.core.trip.domain.PoiIntro;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourIntro;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiDetail;
import com.offway.core.trip.repository.FestivalPlaceRepository;
import com.offway.core.trip.repository.HeritagePlaceRepository;
import com.offway.core.trip.repository.LicensedPlaceRepository;
import com.offway.core.trip.repository.RegionPoiRepository;
import com.offway.core.trip.service.dto.PoiDetail;
import com.offway.core.trip.service.dto.RegionBenefit;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
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

    /** 축제의 뱃지 — 표준데이터에는 업종·종목에 해당하는 값이 없어 종류 자체가 곧 분류다. */
    private static final String FESTIVAL_TYPE_LABEL = "축제";

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

    /** 혜택 기간 판정은 KST — 사용자가 서 있는 시간대다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final TourApiClient tourApiClient;
    private final CatchphraseProvider catchphraseProvider;
    private final LicensedPlaceRepository licensedPlaceRepository;
    private final HeritagePlaceRepository heritagePlaceRepository;
    private final RegionPoiRepository regionPoiRepository;
    private final FestivalPlaceRepository festivalPlaceRepository;
    private final PolicyService policyService;

    /** 캐시를 켜고 끄는 스위치(#403). 조회마다 물어, 운영 중 바뀐 값도 곧바로 듣는다. */
    private final ExternalApiCachePolicy cachePolicy;

    /**
     * 관광 API 상세 캐시 — 인허가·국가유산은 우리 DB 라 캐시하지 않는다.
     *
     * <p><b>stale 을 허용한다.</b> 상세는 느리게 변하므로 6시간 전 값이 502 보다 낫다. 실제로 그 차이가
     * 났다 — 외부가 6초 안에 답하지 않은 순간(2026-08-11 04:14) 사용자는 화면을 통째로 못 봤는데,
     * 캐시가 있었다면 직전 값이 나갔다.
     */
    private final ExternalDataCache<String, CachedDetail> detailCache =
            new ExternalDataCache<>(MAX_CACHED_DETAILS, FIRST_LOAD_WAIT, this::cacheEnabled);

    /**
     * 캐시에 담긴 조회 결과의 상태 — 상태는 boolean 조합이 아니라 이름으로 든다.
     *
     * <p>세 상태의 클라이언트 계약이 각각 다르다(200·404·502). 상태마다 무엇을 내릴지는 상수 자신이 안다.
     */
    private enum DetailStatus {
        FOUND,
        NOT_FOUND,
        LOOKUP_FAILED
    }

    /**
     * 캐시에 담는 조회 결과.
     *
     * <p>{@code detail} 만 담으면 "없는 콘텐츠(404)" 와 "조회 실패(502)" 가 둘 다 null 이 돼 구분되지
     * 않는다. 클라이언트 계약이 갈리는 자리라 상태를 함께 담는다.
     */
    private record CachedDetail(PoiDetail detail, DetailStatus status) {

        static CachedDetail found(PoiDetail detail) {
            return new CachedDetail(Objects.requireNonNull(detail, "detail"), DetailStatus.FOUND);
        }

        static CachedDetail notFound() {
            return new CachedDetail(null, DetailStatus.NOT_FOUND);
        }

        static CachedDetail failed() {
            return new CachedDetail(null, DetailStatus.LOOKUP_FAILED);
        }

        boolean isFound() {
            return status == DetailStatus.FOUND;
        }

        /** 캐시된 상태를 그대로 계약으로 옮긴다 — 서비스에 상태 해석 분기를 남기지 않는다. */
        PoiDetail orThrow() {
            return switch (status) {
                case FOUND -> detail;
                case NOT_FOUND -> throw TourApiException.poiNotFound();
                // 캐시가 잡아 둔 실패다. 원인은 loader 안에서 이미 로그로 남았으니 스택을 다시 찍지 않는다(#362).
                case LOOKUP_FAILED -> throw TourApiException.cachedLookupFailure();
            };
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
        Optional<Long> festivalId = FestivalPlace.parsePublicId(contentId);
        if (festivalId.isPresent()) {
            return festivalDetail(festivalId.get());
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
        return detailCache
                .get(contentId, this::loadDetail, CachedDetail.failed(), StalePolicy.ALLOW_STALE)
                .orThrow();
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
            // contentId 는 공개 콘텐츠 식별자라 가리지 않는다 — 어느 장소가 degrade 했는지가 이 로그의 존재
            // 이유다. 다만 경로 변수라 서블릿이 퍼센트 디코딩을 마친 값이 그대로 온다. 그대로 찍으면 개행
            // 하나로 로그가 여러 줄로 쪼개지므로, 다른 외부 문자열과 같은 새니타이저를 통과시킨다.
            if (stale != null && stale.isFound()) {
                log.warn("관광 API 상세 조회 실패 — 직전 값으로 내려보냅니다 contentId={} cause={}",
                        SensitiveParams.forLog(contentId), e.getClass().getSimpleName());
                return new Loaded<>(stale, FAILURE_CACHE_TTL);
            }
            // 캐시가 비어 있어도 **우리가 그 장소를 이미 안다**. 코스에 실어 보낼 때 쓴 값이 장소 풀에
            // 그대로 있다(#472). 재배포 직후나 처음 열어보는 장소가 여기 닿는데, 그때 502 를 내면
            // 카드에 이름과 사진이 떠 있는 장소를 눌렀더니 화면이 통째로 비는 일이 된다.
            Optional<PoiDetail> stored = storedDetail(contentId);
            if (stored.isPresent()) {
                log.warn("관광 API 상세 조회 실패 — 장소 풀 값으로 내려보냅니다 contentId={} cause={}",
                        SensitiveParams.forLog(contentId), e.getClass().getSimpleName());
                return new Loaded<>(CachedDetail.found(stored.get()), FAILURE_CACHE_TTL);
            }
            // degrade 를 조용히 넘기지 않는다 — 폴백이 정상처럼 보이면 장애를 아무도 모른다.
            log.warn("관광 API 상세 조회 실패 — 내려보낼 직전 값도 장소 풀도 없습니다 contentId={} cause={}",
                    SensitiveParams.forLog(contentId), e.getClass().getSimpleName());
            return new Loaded<>(CachedDetail.failed(), FAILURE_CACHE_TTL);
        }
    }

    /**
     * 장소 풀에 담아 둔 값으로 상세를 짠다 — <b>외부가 죽었을 때만 닿는다</b>(#472).
     *
     * <p><b>있는 것만 채운다.</b> 이름·사진·주소·좌표·전화는 코스에 그 장소를 실을 때 쓴 값 그대로다.
     * 소개글과 운영시간은 장소 풀에 없으므로 비운다 — 없는 것을 지어내지 않는다.
     *
     * <p><b>지도 링크를 붙인다.</b> 소개·운영시간이 빠진 자리를 지도가 대신 답한다. 정상 경로의 관광 API
     * 상세는 그 둘이 이미 있어 링크를 안 주는데, 여기서는 없으니 인허가·국가유산과 같은 규칙을 따른다.
     *
     * <p><b>혜택은 비운다.</b> 지역을 알고 있어 채울 수는 있지만, 정상 경로가 혜택을 안 준다(#172 — 상세
     * 응답에 지역 코드가 없다). 폴백만 채우면 외부가 돌아온 순간 화면에서 혜택이 사라진다.
     */
    private Optional<PoiDetail> storedDetail(String contentId) {
        return regionPoiRepository.findByContentId(contentId).map(PoiDetail::from);
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
                // 혜택은 지역 단위로 매칭되는데 상세 응답에 지역 코드가 없어 어느 지역인지 모른다(#172).
                null,
                catchphraseProvider.forContentId(contentId).orElse(null),
                // 대표 한 장(firstimage)뿐이던 것에 추가 사진을 더한다(#464). 실측으로 완도타워가 16장이다.
                // 상세와 같은 캐시에 실려 나가므로 호출은 캐시 미스 때만 는다.
                tourApiClient.findImages(contentId));
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
                MapSearchLink.of(heritage.getName(), heritage.getAddress()).orElse(null),
                benefitFor(heritage.getRegionId(), SlotKind.SIGHT));
    }

    /**
     * 축제의 상세(#439) — <b>코스에 나가는데 상세가 없던 자리</b>다.
     *
     * <p>축제를 후보로 실으면서 이 분기를 안 붙였다. {@code FST-} 식별자가 관광 API 로 넘어가 <b>외부가
     * 멀쩡할 때도 404</b> 였다 — 카드에 떠 있는 축제를 누르면 없다고 답하는 셈이다. 인허가·국가유산이
     * 같은 이유로 각자 분기를 갖는다.
     *
     * <p>사진은 표준데이터가 주지 않는다. 운영시간도 없다 — 축제는 기간이 그 자리를 대신하는데 상세
     * 계약에 담을 자리가 없어 비운다. 없는 것을 지어내지 않는다.
     */
    private PoiDetail festivalDetail(long id) {
        FestivalPlace festival = festivalPlaceRepository.findById(id).orElseThrow(TourApiException::poiNotFound);
        return PoiDetail.withoutIntro(
                festival.publicId(),
                NON_TOUR_CONTENT_TYPE,
                FESTIVAL_TYPE_LABEL,
                festival.getName(),
                festival.getAddress(),
                festival.getTel(),
                festival.getLat(),
                festival.getLng(),
                null, // 사진 — 표준데이터에 없다
                festival.getDescription(),
                MapSearchLink.of(festival.getName(), festival.getAddress()).orElse(null),
                benefitFor(festival.getRegionId(), SlotKind.SIGHT));
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
                MapSearchLink.of(place.getName(), place.getAddress()).orElse(null),
                benefitFor(place.getRegionId(), place.getKind().slotKind()));
    }

    /**
     * 이 장소에서 쓸 수 있는 혜택 — <b>슬롯 종류가 맞는 것만</b>(#172).
     *
     * <p>지역 혜택은 이미 매칭 규칙이 있다. 여기서는 그중 "이 장소에서 쓸 수 있다" 고 단정할 수 있는 것만
     * 고른다. 지금은 숙박세일페스타(숙소)뿐이다 — 지자체 바우처는 가맹점 목록이, 디지털관광주민증은
     * 제휴처 목록이 있어야 안다.
     *
     * <p>기준일은 오늘이다. 장소 상세에는 여행일이 없다 — 코스에서 누르든 목록에서 누르든 같은 화면이다.
     */
    private RegionBenefit benefitFor(Long regionId, SlotKind slotKind) {
        if (regionId == null) {
            return null;
        }
        return policyService.matchForRegion(regionId, LocalDate.now(SERVICE_ZONE)).stream()
                // 혜택은 policy 소유의 BenefitScope 로 대상을 말하고, 그것이 코스의 어느 자리인지는
                // itinerary 가 안다(#140). 여기서 두 도메인 지식을 다시 잇지 않는다.
                .filter(policy -> policy.getType().targetScope()
                        .map(SlotKind::covering)
                        .filter(slotKind::equals)
                        .isPresent())
                .map(RegionBenefit::from)
                .findFirst()
                .orElse(null);
    }

    /**
     * 캐시를 지금 써도 되나(#403).
     *
     * <p>람다로 필드를 직접 읽지 않고 메서드 참조를 쓰는 이유 — 캐시 필드의 초기화식은 생성자가
     * {@code cachePolicy} 를 넣기 <b>전에</b> 돌아서, 거기서 blank final 을 읽으면 컴파일이 막힌다.
     * 메서드 본문은 그때 읽히지 않는다.
     */
    private boolean cacheEnabled() {
        return cachePolicy.cacheEnabled(ExternalApi.TOUR_API);
    }
}
