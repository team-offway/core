package com.offway.core.weather.infrastructure.airkorea;

import com.offway.core.weather.domain.AirQuality;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@link AirKoreaClient} 외부 경계 stub — 통합 테스트에서 에어코리아 대기질 조회를 격리한다. default 는 throw 라, 대기질까지
 * 도달하는 테스트가 respond(...) 로 동작을 지정하지 않으면 즉시 깨진다(이전 테스트 상태가 살아남는 함정 방지). 시도와 무관하게 같은
 * 결과를 돌려준다(홈 카드가 시도별로 조회하지만 테스트는 단일 시나리오면 충분).
 */
public class StubAirKoreaClient implements AirKoreaClient {

    private Supplier<Optional<AirQuality>> behavior = () -> {
        throw new IllegalStateException("StubAirKoreaClient 미설정 — 테스트가 respond(...) 로 대기질 조회 동작을 지정해야 합니다.");
    };

    /** 모든 시도 조회에 같은 결과를 돌려준다. */
    public void respond(Supplier<Optional<AirQuality>> behavior) {
        this.behavior = behavior;
    }

    @Override
    public Optional<AirQuality> realtimeBySido(String airKoreaSidoName) {
        return behavior.get();
    }
}
