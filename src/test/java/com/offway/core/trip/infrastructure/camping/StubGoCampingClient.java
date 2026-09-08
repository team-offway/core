package com.offway.core.trip.infrastructure.camping;

import com.offway.core.trip.infrastructure.camping.dto.GoCampsiteResult;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * {@link GoCampingClient} 외부 경계 stub — 통합 테스트에서 고캠핑 호출을 격리한다.
 *
 * <p>default 는 throw 라 명시 세팅을 빠뜨리면 즉시 깨진다. 축제 stub 과 달리 페이지 번호를 안 받는
 * 이유는 이 port 가 전량을 한 번에 주기 때문이다 — "둘째 페이지만 깨지는 회차" 라는 상황이 없다.
 */
public class StubGoCampingClient implements GoCampingClient {

    private Supplier<GoCampsiteResult> behavior = () -> {
        throw new IllegalStateException(
                "StubGoCampingClient 미설정 — 테스트가 respond(...) 로 동작을 지정해야 합니다.");
    };

    public void respond(Supplier<GoCampsiteResult> behavior) {
        this.behavior = behavior;
    }

    @Override
    public GoCampsiteResult findAll(Duration maxWait) {
        return behavior.get();
    }
}
