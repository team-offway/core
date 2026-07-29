package com.offway.core.transport.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.offway.core.transport.domain.BusArrival;
import com.offway.core.transport.domain.BusArrivalStatus;
import com.offway.core.transport.domain.BusStop;
import com.offway.core.transport.infrastructure.tago.StubBusArrivalClient;
import java.util.List;
import org.junit.jupiter.api.Test;

/** BusArrivalService — 실시간 도착 조회. 외부 경계는 stub 클라이언트로 격리. */
class BusArrivalServiceTest {

    private static final BusStop TERMINAL = new BusStop("GMB165", "정선터미널", 32020, 37.3801, 128.6604);
    private static final BusStop OFFICE = new BusStop("GMB166", "정선읍사무소", 32020, 37.3812, 128.6631);

    @Test
    void 도착_예정_버스를_돌려준다() {
        StubBusArrivalClient stub = new StubBusArrivalClient();
        stub.respond(() -> new BusArrivalStatus.Arriving(List.of(new BusArrival("1", "농어촌버스", 180, 2))));

        BusArrivalStatus result = new BusArrivalService(stub).arrivalsAt(TERMINAL);

        assertEquals(3, assertInstanceOf(BusArrivalStatus.Arriving.class, result)
                .soonest()
                .arrivalMinutes());
    }

    @Test
    void 조회에_실패하면_Unavailable이다() {
        StubBusArrivalClient stub = new StubBusArrivalClient();
        stub.respond(BusArrivalStatus.Unavailable::new);

        assertInstanceOf(BusArrivalStatus.Unavailable.class, new BusArrivalService(stub).arrivalsAt(TERMINAL));
    }

    @Test
    void 정류소가_다르면_캐시를_공유하지_않는다() {
        StubBusArrivalClient stub = new StubBusArrivalClient();
        stub.respond(() -> new BusArrivalStatus.Arriving(List.of(new BusArrival("1", "농어촌버스", 180, 2))));
        BusArrivalService service = new BusArrivalService(stub);

        service.arrivalsAt(TERMINAL);
        service.arrivalsAt(OFFICE);

        assertEquals(2, stub.callCount());
    }

    @Test
    void 같은_정류소_연속_조회는_짧은_캐시로_묶인다() {
        // 실시간이라 TTL 이 20초로 짧지만, 동시 요청이 몰릴 때 쿼터가 터지는 건 막아야 한다.
        StubBusArrivalClient stub = new StubBusArrivalClient();
        stub.respond(() -> new BusArrivalStatus.Arriving(List.of(new BusArrival("1", "농어촌버스", 180, 2))));
        BusArrivalService service = new BusArrivalService(stub);

        service.arrivalsAt(TERMINAL);
        service.arrivalsAt(TERMINAL);

        assertEquals(1, stub.callCount());
    }
}
