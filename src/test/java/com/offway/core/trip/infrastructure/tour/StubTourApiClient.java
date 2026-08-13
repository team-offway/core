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

    /** 지역기반 조회 호출 횟수 — 재생성이 후보를 몇 번 모으는지 세는 데 쓴다(#114). */
    private final java.util.concurrent.atomic.AtomicInteger areaCalls = new java.util.concurrent.atomic.AtomicInteger();

    /** 공통상세 호출 횟수 — 캐시가 실제로 외부를 아끼는지 세는 데 쓴다. */
    private final java.util.concurrent.atomic.AtomicInteger detailCalls = new java.util.concurrent.atomic.AtomicInteger();

    private Supplier<Optional<TourPoiDetail>> detailBehavior = Optional::empty;
    private Supplier<Optional<TourIntro>> introBehavior = Optional::empty;
    private Supplier<Optional<TourAccessibility>> accessibilityBehavior = Optional::empty;

    /** 모든 지역기반 조회에 같은 결과를 돌려준다. */
    public void respond(Supplier<TourPoiResult> areaBehavior) {
        this.areaBehavior = areaBehavior;
    }

    /** 지금까지의 지역기반 조회 횟수. */
    public int areaCallCount() {
        return areaCalls.get();
    }

    /** 호출 횟수를 0 으로 — 공유 컨텍스트라 테스트마다 자기 시나리오만 세게 한다. */
    public void resetAreaCallCount() {
        areaCalls.set(0);
    }

    /** 지금까지의 공통상세 조회 횟수. */
    public int detailCallCount() {
        return detailCalls.get();
    }

    /** 호출 횟수를 0 으로 — 공유 컨텍스트라 테스트마다 자기 시나리오만 세게 한다. */
    public void resetDetailCallCount() {
        detailCalls.set(0);
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
        // 실 API 처럼 타입 필터 → 페이지 크기(numOfRows) 제한 순으로 재현한다. numOfRows 를 지켜야, 전체타입 단일 조회로
        // 관광지가 페이지를 채우면 음식점·숙박이 밀려나는 과소표집을 테스트가 실제로 잡는다(타입별 조회 변경의 회귀 방지).
        areaCalls.incrementAndGet();
        TourPoiResult source = areaBehavior.get();
        if (contentTypeId == null) {
            // 전체 조회 — totalCount(선언된 전체 건수)는 그대로 두고 페이지만 자른다.
            return new TourPoiResult(source.items().stream().limit(numOfRows).toList(), source.totalCount());
        }
        List<TourPoi> matched = source.items().stream()
                .filter(poi -> contentTypeId.equals(poi.contentTypeId()))
                .toList();
        return new TourPoiResult(matched.stream().limit(numOfRows).toList(), matched.size());
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
        detailCalls.incrementAndGet();
        return detailBehavior.get();
    }

    @Override
    public Optional<TourAccessibility> findAccessibility(String contentId) {
        return accessibilityBehavior.get();
    }
}
