package com.offway.core.transport.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.offway.core.transport.infrastructure.tago.StubTrainInfoClient;
import com.offway.core.transport.infrastructure.tago.dto.Station;
import com.offway.core.transport.infrastructure.tago.dto.TrainAvailability;
import com.offway.core.transport.infrastructure.tago.dto.TrainLeg;
import com.offway.core.transport.service.dto.TrainAccess;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** TrainAccessService — 역 해석(이름매칭·출발지 거점) + 열차 조회를 조립한 4-way 결과 검증. 외부는 StubTrainInfoClient 로 격리. */
class TrainAccessServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 5, 1);
    private static final double SEOUL_LAT = 37.5547;
    private static final double SEOUL_LNG = 126.9707;
    // 강원 역 목록(정선 있음). 출발 거점역(서울)은 resolver 가 큐레이션으로 안다.
    private static final List<Station> GANGWON = List.of(
            new Station("NAT770658", "정선"), new Station("NAT600000", "태백"));

    private static TrainAccessService service(StubTrainInfoClient stub) {
        return new TrainAccessService(new TrainStationResolver(stub), new TrainRouteService(stub));
    }

    private static TrainLeg ktx() {
        return TrainLeg.of("KTX",
                LocalDateTime.of(2026, 5, 1, 7, 0), LocalDateTime.of(2026, 5, 1, 9, 30));
    }

    @Test
    void 서울출발_정선도착_운행있으면_AVAILABLE() {
        StubTrainInfoClient stub = new StubTrainInfoClient();
        stub.respondStations(city -> GANGWON);
        stub.respond(() -> new TrainAvailability.Available(ktx()));

        TrainAccess access = service(stub).accessTo(SEOUL_LAT, SEOUL_LNG, "강원특별자치도", "정선군", DATE);

        assertEquals(TrainAccess.Status.AVAILABLE, access.status());
        assertEquals("서울", access.fromStation());
        assertEquals("정선", access.toStation());
        assertEquals("KTX", access.fastest().trainType());
        assertEquals(150, access.fastest().durationMinutes());
    }

    @Test
    void 동명_역이_없는_지역은_NO_STATION() {
        StubTrainInfoClient stub = new StubTrainInfoClient();
        stub.respondStations(city -> GANGWON); // 정선·태백뿐 — 철원 없음
        stub.respond(() -> {
            throw new AssertionError("역이 없으면 열차 조회를 하면 안 된다");
        });

        TrainAccess access = service(stub).accessTo(SEOUL_LAT, SEOUL_LNG, "강원특별자치도", "철원군", DATE);

        assertEquals(TrainAccess.Status.NO_STATION, access.status());
        assertEquals("서울", access.fromStation()); // 출발역은 잡히고
        assertEquals(null, access.toStation()); // 도착역이 없다
    }

    @Test
    void 출발지가_거점역에서_멀면_NO_STATION() {
        StubTrainInfoClient stub = new StubTrainInfoClient();
        stub.respondStations(city -> GANGWON);
        stub.respond(() -> {
            throw new AssertionError("출발역이 없으면 열차 조회를 하면 안 된다");
        });

        // 울릉도 근처 — 거점역 반경 밖
        TrainAccess access = service(stub).accessTo(37.48, 130.90, "강원특별자치도", "정선군", DATE);

        assertEquals(TrainAccess.Status.NO_STATION, access.status());
    }

    @Test
    void 역은_있는데_그날_미운행이면_NO_SERVICE_ON_DATE() {
        StubTrainInfoClient stub = new StubTrainInfoClient();
        stub.respondStations(city -> GANGWON);
        stub.respond(() -> new TrainAvailability.NoServiceOnDate());

        TrainAccess access = service(stub).accessTo(SEOUL_LAT, SEOUL_LNG, "강원특별자치도", "정선군", DATE);

        assertEquals(TrainAccess.Status.NO_SERVICE_ON_DATE, access.status());
        assertEquals("정선", access.toStation());
    }

    @Test
    void 열차_조회_실패는_UNAVAILABLE() {
        StubTrainInfoClient stub = new StubTrainInfoClient();
        stub.respondStations(city -> GANGWON);
        stub.respond(() -> new TrainAvailability.Unavailable());

        TrainAccess access = service(stub).accessTo(SEOUL_LAT, SEOUL_LNG, "강원특별자치도", "정선군", DATE);

        assertEquals(TrainAccess.Status.UNAVAILABLE, access.status());
    }
}
