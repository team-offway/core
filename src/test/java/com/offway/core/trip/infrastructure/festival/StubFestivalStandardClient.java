package com.offway.core.trip.infrastructure.festival;

import com.offway.core.trip.infrastructure.festival.dto.StandardFestivalResult;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * {@link FestivalStandardClient} 외부 경계 stub — 통합 테스트에서 표준데이터 호출을 격리한다.
 *
 * <p>default 는 throw 라 명시 세팅을 빠뜨리면 즉시 깨진다.
 *
 * <p><b>페이지 인자가 없다.</b> 파일 방식으로 옮기면서 전량이 한 번에 오게 됐다(#433) — "둘째
 * 페이지만 깨지는 회차" 같은 시나리오가 사라졌고, 실패는 이제 회차 전체의 실패다.
 */
public class StubFestivalStandardClient implements FestivalStandardClient {

    private Supplier<StandardFestivalResult> behavior = () -> {
        throw new IllegalStateException(
                "StubFestivalStandardClient 미설정 — 테스트가 respond(...) 로 동작을 지정해야 합니다.");
    };

    public void respond(Supplier<StandardFestivalResult> behavior) {
        this.behavior = behavior;
    }

    @Override
    public StandardFestivalResult findAll(Duration maxWait) {
        return behavior.get();
    }
}
