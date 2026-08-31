package com.offway.core.transport.infrastructure.tago;

import com.offway.core.transport.domain.TransitLegResult;
import com.offway.core.transport.domain.TransitMode;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * {@link TransitLegClient} 외부 경계 stub — 통합 테스트에서 TAGO 버스·여객선 구간 호출을 격리한다. default 는
 * throw 라 명시 세팅을 빠뜨리면 즉시 깨진다(이전 테스트 상태가 살아남는 함정 방지).
 *
 * <p>물어본 날짜를 기억한다({@link #askedDates()}). 조회창을 며칠까지 미는지는 <b>몇 건이 나갔는가</b> 로만
 * 확인할 수 있어, 결과값만 봐서는 회귀를 못 잡는다.
 */
public class StubTransitLegClient implements TransitLegClient {

    private final List<LocalDate> askedDates = new CopyOnWriteArrayList<>();

    private Supplier<TransitLegResult> behavior = () -> {
        throw new IllegalStateException("StubTransitLegClient 미설정 — 테스트가 respond(...) 로 동작을 지정해야 합니다.");
    };

    /** 동작을 지정하면서 앞선 테스트가 남긴 호출 기록을 지운다 — 컨텍스트를 공유하므로 여기서 끊어야 한다. */
    public void respond(Supplier<TransitLegResult> behavior) {
        this.behavior = behavior;
        askedDates.clear();
    }

    /** 마지막 {@link #respond} 이후 물어본 날짜들 — 순서 그대로. */
    public List<LocalDate> askedDates() {
        return List.copyOf(askedDates);
    }

    @Override
    public TransitLegResult measure(TransitMode mode, String depCode, String arrCode, LocalDate date) {
        askedDates.add(date);
        return behavior.get();
    }
}
