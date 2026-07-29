package com.offway.core.transport.infrastructure.tago;

import com.offway.core.transport.domain.BusStopAccess;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * {@link BusStopClient} 외부 경계 stub — 테스트에서 TAGO 정류소 조회를 격리한다.
 *
 * <p>default 동작은 throw 다. 조회 경로에 닿는 테스트가 {@code respond(...)} 로 시나리오를 지정하지 않으면 즉시 깨지게 해
 * "이전 테스트 상태가 살아남는" 함정을 막는다.
 */
public class StubBusStopClient implements BusStopClient {

    private final AtomicInteger callCount = new AtomicInteger();

    private Supplier<BusStopAccess> behavior = () -> {
        throw new IllegalStateException("StubBusStopClient 미설정 — 테스트가 respond(...) 로 조회 동작을 지정해야 합니다.");
    };

    public void respond(Supplier<BusStopAccess> behavior) {
        this.behavior = behavior;
    }

    /** 실제 호출 횟수 — 캐시가 외부 호출을 실제로 줄이는지 확인용. */
    public int callCount() {
        return callCount.get();
    }

    @Override
    public BusStopAccess nearbyStops(double lat, double lng) {
        callCount.incrementAndGet();
        return behavior.get();
    }
}
