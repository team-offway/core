package com.offway.core.transport.infrastructure.tago;

import com.offway.core.transport.infrastructure.tago.dto.TrainAvailability;
import java.time.LocalDate;
import java.util.function.Supplier;

/**
 * {@link TrainInfoClient} 외부 경계 stub — 통합 테스트에서 TAGO 열차정보 호출을 격리한다. default 는 throw 라 명시 세팅을
 * 빠뜨리면 즉시 깨진다(이전 테스트 상태가 살아남는 함정 방지).
 */
public class StubTrainInfoClient implements TrainInfoClient {

    private Supplier<TrainAvailability> behavior = () -> {
        throw new IllegalStateException("StubTrainInfoClient 미설정 — 테스트가 respond(...) 로 동작을 지정해야 합니다.");
    };

    public void respond(Supplier<TrainAvailability> behavior) {
        this.behavior = behavior;
    }

    @Override
    public TrainAvailability fastestTrain(String depStationId, String arrStationId, LocalDate date) {
        return behavior.get();
    }
}
