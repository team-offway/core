package com.offway.core.trip.infrastructure.datalab;

import com.offway.core.trip.infrastructure.datalab.dto.TourVisitorResult;
import java.time.LocalDate;
import java.util.function.Supplier;

/**
 * {@link TourDataLabClient} 외부 경계 stub — 통합 테스트에서 관광빅데이터 호출을 격리한다. default 는 throw 라 명시 세팅을
 * 빠뜨리면 즉시 깨진다.
 */
public class StubTourDataLabClient implements TourDataLabClient {

    private Supplier<TourVisitorResult> behavior = () -> {
        throw new IllegalStateException("StubTourDataLabClient 미설정 — 테스트가 respond(...) 로 동작을 지정해야 합니다.");
    };

    public void respond(Supplier<TourVisitorResult> behavior) {
        this.behavior = behavior;
    }

    @Override
    public TourVisitorResult findRegionVisitors(LocalDate from, LocalDate to, int pageNo, int numOfRows) {
        return behavior.get();
    }
}
