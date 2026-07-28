package com.offway.core.trip.service;

import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourIntro;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiDetail;
import com.offway.core.trip.service.dto.PoiDetail;
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

    private final TourApiClient tourApiClient;
    private final CatchphraseProvider catchphraseProvider;

    public PoiDetail detail(String contentId) {
        TourPoiDetail detail = tourApiClient.findDetail(contentId).orElseThrow(TourApiException::poiNotFound);

        TourIntro intro = detail.contentTypeId() == null
                ? null
                : tourApiClient.findIntro(contentId, detail.contentTypeId()).orElse(null);

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
                intro == null ? null : intro.useTime(),
                intro == null ? null : intro.restDate(),
                catchphraseProvider.forContentId(contentId).orElse(null));
    }
}
