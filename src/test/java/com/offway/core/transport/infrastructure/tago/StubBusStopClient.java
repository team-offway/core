package com.offway.core.transport.infrastructure.tago;

import com.offway.core.transport.domain.BusCoverage;
import com.offway.core.transport.domain.BusStopAccess;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * {@link BusStopClient} 외부 경계 stub — 테스트에서 TAGO 정류소 조회를 격리한다.
 *
 * <p>default 동작은 throw 다. 조회 경로에 닿는 테스트가 {@code respond(...)} 로 시나리오를 지정하지 않으면 즉시 깨지게 해
 * "이전 테스트 상태가 살아남는" 함정을 막는다. 커버 목록({@code respondCoverage})도 같다.
 */
public class StubBusStopClient implements BusStopClient {

    private final AtomicInteger callCount = new AtomicInteger();
    private final AtomicInteger coverageCallCount = new AtomicInteger();

    private Supplier<BusStopAccess> behavior = () -> {
        throw new IllegalStateException("StubBusStopClient 미설정 — 테스트가 respond(...) 로 조회 동작을 지정해야 합니다.");
    };

    private Supplier<Optional<BusCoverage>> coverageBehavior = () -> {
        throw new IllegalStateException(
                "StubBusStopClient 미설정 — 테스트가 respondCoverage(...) 로 커버 목록을 지정해야 합니다.");
    };

    public void respond(Supplier<BusStopAccess> behavior) {
        this.behavior = behavior;
    }

    public void respondCoverage(Supplier<Optional<BusCoverage>> coverageBehavior) {
        this.coverageBehavior = coverageBehavior;
    }

    /** 실제 호출 횟수 — 캐시가 외부 호출을 실제로 줄이는지 확인용. */
    public int callCount() {
        return callCount.get();
    }

    /** 커버 목록 호출 횟수 — 거의 안 변하는 목록을 매번 다시 부르지 않는지 확인용. */
    public int coverageCallCount() {
        return coverageCallCount.get();
    }

    @Override
    public BusStopAccess nearbyStops(double lat, double lng) {
        callCount.incrementAndGet();
        return behavior.get();
    }

    @Override
    public Optional<BusCoverage> coveredCities() {
        coverageCallCount.incrementAndGet();
        return coverageBehavior.get();
    }
}
