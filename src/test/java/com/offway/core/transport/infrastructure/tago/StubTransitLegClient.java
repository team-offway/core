package com.offway.core.transport.infrastructure.tago;

import com.offway.core.transport.domain.TransitLegResult;
import com.offway.core.transport.domain.TransitMode;
import java.time.LocalDate;
import java.util.function.Supplier;

/**
 * {@link TransitLegClient} 외부 경계 stub — 통합 테스트에서 TAGO 버스·여객선 구간 호출을 격리한다. default 는
 * throw 라 명시 세팅을 빠뜨리면 즉시 깨진다(이전 테스트 상태가 살아남는 함정 방지).
 */
public class StubTransitLegClient implements TransitLegClient {

    private Supplier<TransitLegResult> behavior = () -> {
        throw new IllegalStateException("StubTransitLegClient 미설정 — 테스트가 respond(...) 로 동작을 지정해야 합니다.");
    };

    public void respond(Supplier<TransitLegResult> behavior) {
        this.behavior = behavior;
    }

    @Override
    public TransitLegResult measure(TransitMode mode, String depCode, String arrCode, LocalDate date) {
        return behavior.get();
    }
}
