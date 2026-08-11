package com.offway.core.trip.service;

import com.offway.core.itinerary.domain.SlotKind;
import com.offway.core.policy.service.PolicyService;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 장소 상세 조회(F4 표시요소·course-logic ③) — 공통상세(detailCommon2)에 소개정보(detailIntro2)의 운영시간·휴무일을
 * 합친다. 코스 타임라인에서 장소를 누르면 상세를 보여준다.
 *
 * <p>TourAPI 는 read-timeout 이 길어 트랜잭션 밖에서 호출한다(persistence-convention). 장소가 없으면
 * {@link TourApiException#poiNotFound()}(404).
 */
@Service
@RequiredArgsConstructor
public class PoiDetailService {

    /** TourAPI 콘텐츠가 아님을 뜻하는 타입 — 인허가·국가유산이 함께 쓴다. 실제 contentTypeId 는 12·32·39 처럼 모두 양수다. */
    private static final int NON_TOUR_CONTENT_TYPE = 0;

    /** 혜택 기간 판정은 KST — 사용자가 서 있는 시간대다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final TourApiClient tourApiClient;
    private final CatchphraseProvider catchphraseProvider;
    private final LicensedPlaceRepository licensedPlaceRepository;
    private final HeritagePlaceRepository heritagePlaceRepository;
    private final PolicyService policyService;

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

        TourPoiDetail detail = tourApiClient.findDetail(contentId).orElseThrow(TourApiException::poiNotFound);

        // 외부 응답을 여기서 도메인으로 옮긴다 — 상위 레이어(서비스 dto·응답 dto)가 어댑터 DTO 를 들지 않게.
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
                catchphraseProvider.forContentId(contentId).orElse(null));
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
    private String benefitFor(Long regionId, SlotKind slotKind) {
        if (regionId == null) {
            return null;
        }
        return policyService.matchForRegion(regionId, LocalDate.now(SERVICE_ZONE)).stream()
                .filter(policy -> policy.getType().targetSlotKind().filter(slotKind::equals).isPresent())
                .map(policy -> policy.getType().badgeText())
                .findFirst()
                .orElse(null);
    }
}
