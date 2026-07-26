package com.offway.core.trip.infrastructure.tour;

import com.offway.core.trip.infrastructure.tour.dto.TourIntro;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiResult;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@link TourApiClient} 외부 경계 stub — 통합 테스트에서 TourAPI 콘텐츠 조회를 격리한다. 콘텐츠 경로가 쓰는 {@code findByArea}
 * 는 default 가 throw 라, 콘텐츠까지 도달하는 테스트가 respond(...) 로 동작을 지정하지 않으면 즉시 깨진다. 콘텐츠 경로 밖인
 * {@code findByLocation}·{@code findIntro} 는 빈 결과(이 스코프에서 미사용).
 */
public class StubTourApiClient implements TourApiClient {

    private Supplier<TourPoiResult> areaBehavior = () -> {
        throw new IllegalStateException("StubTourApiClient 미설정 — 테스트가 respond(...) 로 지역기반 조회 동작을 지정해야 합니다.");
    };

    /** 모든 지역기반 조회에 같은 결과를 돌려준다. */
    public void respond(Supplier<TourPoiResult> areaBehavior) {
        this.areaBehavior = areaBehavior;
    }

    @Override
    public TourPoiResult findByArea(int areaCode, Integer sigunguCode, Integer contentTypeId, int numOfRows) {
        return areaBehavior.get();
    }

    @Override
    public TourPoiResult findByLocation(double lat, double lng, int radiusMeters, Integer contentTypeId, int numOfRows) {
        return TourPoiResult.empty();
    }

    @Override
    public Optional<TourIntro> findIntro(String contentId, int contentTypeId) {
        return Optional.empty();
    }
}
