package com.offway.core.transport.infrastructure.tago;

import com.offway.core.transport.domain.TrainAvailability;
import java.time.LocalDate;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * {@link TrainInfoClient} 외부 경계 stub — 통합 테스트에서 TAGO 열차정보 호출을 격리한다. default 는 throw 라 명시 세팅을
 * 빠뜨리면 즉시 깨진다(이전 테스트 상태가 살아남는 함정 방지).
 */
public class StubTrainInfoClient implements TrainInfoClient {

    private Function<String, TrainAvailability> behavior = departure -> {
        throw new IllegalStateException("StubTrainInfoClient 미설정 — 테스트가 respond(...) 로 동작을 지정해야 합니다.");
    };

    public void respond(Supplier<TrainAvailability> behavior) {
        this.behavior = departure -> behavior.get();
    }

    /**
     * 출발역마다 다르게 답한다(#435).
     *
     * <p>"수서에는 제천행이 없고 왕십리에는 있다" 처럼 <b>역에 따라 갈리는</b> 상황을 재현하려면 역쌍을
     * 구분하지 못하는 {@link #respond(Supplier)} 로는 부족하다.
     */
    public void respondByDeparture(Function<String, TrainAvailability> behavior) {
        this.behavior = behavior;
    }

    @Override
    public TrainAvailability fastestTrain(String depStationId, String arrStationId, LocalDate date) {
        return behavior.apply(depStationId);
    }
}
