package com.offway.core.transport.infrastructure.tago;

import com.offway.core.transport.domain.Departure;
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

    private final List<LocalDate> departureAsks = new CopyOnWriteArrayList<>();

    private Supplier<TransitLegResult> behavior = () -> {
        throw new IllegalStateException("StubTransitLegClient 미설정 — 테스트가 respond(...) 로 동작을 지정해야 합니다.");
    };

    /**
     * 시간표는 default 가 <b>빈 목록</b>이다 — throw 가 아니다.
     *
     * <p>다른 stub 과 규칙이 다른 이유가 있다. 시간표는 조회창 밖이면 안 불리는 것이 정상 동작이라,
     * throw 로 두면 <b>안 불려야 정상인 테스트</b>가 전부 이 stub 을 세팅해야 한다. 빈 목록은 "그날 편이
     * 없다" 와 같은 뜻이고 화면도 그때 시간표 줄만 접는다.
     */
    private Supplier<List<Departure>> departures = List::of;

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

    /**
     * 시간표 동작을 지정하면서 앞선 테스트가 남긴 호출 기록을 지운다.
     *
     * <p>{@link #respond} 와 따로 둔다 — 시간표를 안 쓰는 테스트가 대부분이라, 하나로 묶으면 그쪽까지
     * 매번 시간표를 세팅해야 한다.
     */
    public void respondDepartures(Supplier<List<Departure>> departures) {
        this.departures = departures;
        departureAsks.clear();
    }

    /** 마지막 {@link #respondDepartures} 이후 시간표를 물어본 날짜들 — <b>안 물어본 것</b>을 확인하는 데 쓴다. */
    public List<LocalDate> departureAsks() {
        return List.copyOf(departureAsks);
    }

    @Override
    public List<Departure> departures(TransitMode mode, String depCode, String arrCode, LocalDate date) {
        departureAsks.add(date);
        return this.departures.get();
    }
}
