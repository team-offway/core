package com.offway.core.trip.service;

import com.offway.core.trip.domain.HeritagePlace;
import com.offway.core.trip.domain.LicensedPlace;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.domain.PoiIntro;
import com.offway.core.trip.infrastructure.tour.dto.TourIntro;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiDetail;
import com.offway.core.trip.repository.HeritagePlaceRepository;
import com.offway.core.trip.repository.LicensedPlaceRepository;
import com.offway.core.trip.service.dto.PoiDetail;
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

    /** TourAPI 콘텐츠가 아님을 뜻하는 타입. 실제 contentTypeId 는 12·32·39 처럼 모두 양수다. */
    private static final int LICENSED_CONTENT_TYPE = 0;

    private final TourApiClient tourApiClient;
    private final CatchphraseProvider catchphraseProvider;
    private final LicensedPlaceRepository licensedPlaceRepository;
    private final HeritagePlaceRepository heritagePlaceRepository;

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
                detail.title(),
                detail.address(),
                detail.tel(),
                detail.lat(),
                detail.lng(),
                detail.imageUrl(),
                detail.overview(),
                intro,
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
        return new PoiDetail(
                heritage.publicId(),
                LICENSED_CONTENT_TYPE,
                heritage.getName(),
                heritage.getAddress(),
                null, // 전화
                heritage.getLat(),
                heritage.getLng(),
                heritage.getImageUrl(),
                heritage.getDescription(),
                null, // 운영시간
                null, // 휴무일
                null); // 캐치프레이즈
    }

    /** 인허가 장소의 상세 — 우리가 가진 것만 채우고 나머지는 비운다. 없는 것을 지어내지 않는다. */
    private PoiDetail licensedDetail(long id) {
        LicensedPlace place = licensedPlaceRepository.findById(id).orElseThrow(TourApiException::poiNotFound);
        return PoiDetail.withoutIntro(
                place.publicId(),
                LICENSED_CONTENT_TYPE,
                place.getName(),
                place.getAddress(),
                place.getTel(),
                place.getLat(),
                place.getLng(),
                null,  // 사진
                null); // 소개글
    }
}
