package com.offway.core.trip.infrastructure.tour;

import com.offway.core.trip.infrastructure.tour.dto.TourAccessibility;
import com.offway.core.trip.infrastructure.tour.dto.TourIntro;
import com.offway.core.trip.infrastructure.tour.dto.TourPoi;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiDetail;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiResult;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@link TourApiClient} 외부 경계 stub — 통합 테스트에서 TourAPI 콘텐츠 조회를 격리한다. 콘텐츠 경로가 쓰는 {@code findByArea}
 * 는 default 가 throw 라, 콘텐츠까지 도달하는 테스트가 respond(...) 로 동작을 지정하지 않으면 즉시 깨진다. 콘텐츠 경로 밖인
 * {@code findByLocation}·{@code findIntro} 는 빈 결과(이 스코프에서 미사용).
 *
 * <p>{@code findByArea} 는 실 API 처럼 {@code contentTypeId} 로 걸러 준다 — 타입별로 나눠 조회하는 호출부(볼거리/맛집/숙박)가
 * 섞인 픽스처 하나로도 각 풀을 제대로 받게 한다({@code null} 이면 전체).
 */
public class StubTourApiClient implements TourApiClient {

    private Supplier<TourPoiResult> areaBehavior = () -> {
        throw new IllegalStateException("StubTourApiClient 미설정 — 테스트가 respond(...) 로 지역기반 조회 동작을 지정해야 합니다.");
    };

    private Supplier<Optional<TourPoiDetail>> detailBehavior = Optional::empty;
    private Supplier<Optional<TourIntro>> introBehavior = Optional::empty;
    private Supplier<Optional<TourAccessibility>> accessibilityBehavior = Optional::empty;

    /** 모든 지역기반 조회에 같은 결과를 돌려준다. */
    public void respond(Supplier<TourPoiResult> areaBehavior) {
        this.areaBehavior = areaBehavior;
    }

    /** 공통상세(detailCommon2) 응답을 지정한다. */
    public void respondDetail(Supplier<Optional<TourPoiDetail>> detailBehavior) {
        this.detailBehavior = detailBehavior;
    }

    /** 소개정보(detailIntro2) 응답을 지정한다. */
    public void respondIntro(Supplier<Optional<TourIntro>> introBehavior) {
        this.introBehavior = introBehavior;
    }

    /** 무장애정보(detailWithTour2) 응답을 지정한다. */
    public void respondAccessibility(Supplier<Optional<TourAccessibility>> accessibilityBehavior) {
        this.accessibilityBehavior = accessibilityBehavior;
    }

    @Override
    public TourPoiResult findByArea(int areaCode, Integer sigunguCode, Integer contentTypeId, int numOfRows) {
        TourPoiResult all = areaBehavior.get();
        if (contentTypeId == null) {
            return all;
        }
        List<TourPoi> filtered = all.items().stream()
                .filter(poi -> contentTypeId.equals(poi.contentTypeId()))
                .toList();
        return new TourPoiResult(filtered, filtered.size());
    }

    @Override
    public TourPoiResult findByLocation(double lat, double lng, int radiusMeters, Integer contentTypeId, int numOfRows) {
        return TourPoiResult.empty();
    }

    @Override
    public Optional<TourIntro> findIntro(String contentId, int contentTypeId) {
        return introBehavior.get();
    }

    @Override
    public Optional<TourPoiDetail> findDetail(String contentId) {
        return detailBehavior.get();
    }

    @Override
    public Optional<TourAccessibility> findAccessibility(String contentId) {
        return accessibilityBehavior.get();
    }
}
