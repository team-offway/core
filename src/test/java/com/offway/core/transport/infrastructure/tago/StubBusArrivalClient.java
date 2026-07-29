package com.offway.core.transport.infrastructure.tago;

import com.offway.core.transport.domain.BusArrivalStatus;
import com.offway.core.transport.domain.BusStop;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * {@link BusArrivalClient} 외부 경계 stub — 테스트에서 TAGO 도착정보 조회를 격리한다.
 *
 * <p>default 동작은 throw 다(미설정 테스트를 즉시 깨뜨린다).
 */
public class StubBusArrivalClient implements BusArrivalClient {

    private final AtomicInteger callCount = new AtomicInteger();

    private Supplier<BusArrivalStatus> behavior = () -> {
        throw new IllegalStateException("StubBusArrivalClient 미설정 — 테스트가 respond(...) 로 조회 동작을 지정해야 합니다.");
    };

    public void respond(Supplier<BusArrivalStatus> behavior) {
        this.behavior = behavior;
    }

    /** 실제 호출 횟수 — 캐시가 외부 호출을 실제로 줄이는지 확인용. */
    public int callCount() {
        return callCount.get();
    }

    @Override
    public BusArrivalStatus arrivalsAt(BusStop stop) {
        callCount.incrementAndGet();
        return behavior.get();
    }
}
