package com.offway.core.trip.infrastructure.festival;

import com.offway.core.trip.infrastructure.festival.dto.StandardFestivalResult;
import java.time.Duration;
import java.util.function.IntFunction;

/**
 * {@link FestivalStandardClient} 외부 경계 stub — 통합 테스트에서 표준데이터 호출을 격리한다.
 *
 * <p>default 는 throw 라 명시 세팅을 빠뜨리면 즉시 깨진다. 페이지 번호를 받는 이유는 "둘째 페이지가
 * 깨지는 회차" 처럼 <b>페이지마다 다른 답</b>을 줘야 하는 시나리오가 있어서다.
 */
public class StubFestivalStandardClient implements FestivalStandardClient {

    private IntFunction<StandardFestivalResult> behavior = page -> {
        throw new IllegalStateException(
                "StubFestivalStandardClient 미설정 — 테스트가 respond(...) 로 동작을 지정해야 합니다.");
    };

    public void respond(IntFunction<StandardFestivalResult> behavior) {
        this.behavior = behavior;
    }

    @Override
    public StandardFestivalResult findAll(int pageNo, int numOfRows, Duration maxWait) {
        return behavior.apply(pageNo);
    }
}
