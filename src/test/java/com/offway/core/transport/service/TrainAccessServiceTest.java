package com.offway.core.transport.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.domain.TrainAvailability;
import com.offway.core.transport.domain.TrainLeg;
import com.offway.core.transport.domain.TrainStation;
import com.offway.core.transport.infrastructure.tago.StubTrainInfoClient;
import com.offway.core.transport.repository.TrainStationRepository;
import com.offway.core.transport.service.dto.TrainAccess;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** TrainAccessService — 좌표 최근접 역해석 + 열차 조회를 조립한 4-way 결과. 역 마스터는 stub 리포지토리로, 열차는 stub 클라이언트로 격리. */
class TrainAccessServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 5, 1);
    private static final double SEOUL_LAT = 37.5547;
    private static final double SEOUL_LNG = 126.9707;
    private static final double JEONGSEON_LAT = 37.3878;
    private static final double JEONGSEON_LNG = 128.6716;

    /** 서울·경주·정선 3역 마스터. */
    private static final List<TrainStation> MASTER = List.of(
            TrainStation.of("NAT010000", "서울", 37.5547, 126.9707),
            TrainStation.of("NATH13421", "경주", 35.7980, 129.1405),
            TrainStation.of("NAT610226", "정선", 37.3878, 128.6716)); // 코드는 시드 마스터와 일치

    private static TrainAccessService service(StubTrainInfoClient stub) {
        TrainStationRepository repo = () -> MASTER; // findAll 단일 메서드 → 람다
        return new TrainAccessService(new TrainStationResolver(repo), new TrainRouteService(stub));
    }

    private static TrainLeg ktx() {
        return TrainLeg.of("KTX", LocalDateTime.of(2026, 5, 1, 7, 0), LocalDateTime.of(2026, 5, 1, 9, 30));
    }

    @Test
    void 운행이_없어도_도착역_좌표는_그대로_준다() {
        // 도착 지점은 "그 지역에 열차로 가면 어디에 내리나" 라서 그날 운행 여부와 무관하다. 여기서 빈 값을 주면
        // 코스가 출발지 좌표로 되돌아가 반대편 동선을 짠다(#127).
        StubTrainInfoClient stub = new StubTrainInfoClient();
        stub.respond(TrainAvailability.NoServiceOnDate::new);

        TrainAccess access = service(stub).accessTo(SEOUL_LAT, SEOUL_LNG, JEONGSEON_LAT, JEONGSEON_LNG, DATE);

        assertEquals(new Coordinate(JEONGSEON_LAT, JEONGSEON_LNG), access.arrivalPoint().orElseThrow());
        assertTrue(access.arrivalAt().isEmpty(), "운행을 못 찾았으면 도착 시각은 모른다");
    }

    @Test
    void 조회가_실패해도_도착역_좌표는_그대로_준다() {
        StubTrainInfoClient stub = new StubTrainInfoClient();
        stub.respond(TrainAvailability.Unavailable::new);

        TrainAccess access = service(stub).accessTo(SEOUL_LAT, SEOUL_LNG, JEONGSEON_LAT, JEONGSEON_LNG, DATE);

        assertEquals(new Coordinate(JEONGSEON_LAT, JEONGSEON_LNG), access.arrivalPoint().orElseThrow());
    }

    @Test
    void 역이_없으면_도착_지점도_없다() {
        // 태평양 한가운데 — 50㎞ 안에 역이 없다. 이때만 도착 지점이 비어 코스가 출발지 기준으로 돌아간다.
        StubTrainInfoClient stub = new StubTrainInfoClient();

        TrainAccess access = service(stub).accessTo(SEOUL_LAT, SEOUL_LNG, 20.0, 150.0, DATE);

        assertEquals(TrainAccess.Status.NO_STATION, access.status());
        assertTrue(access.arrivalPoint().isEmpty());
    }

    @Test
    void 운행하면_도착_시각을_준다() {
        StubTrainInfoClient stub = new StubTrainInfoClient();
        stub.respond(() -> new TrainAvailability.Available(ktx()));

        TrainAccess access = service(stub).accessTo(SEOUL_LAT, SEOUL_LNG, JEONGSEON_LAT, JEONGSEON_LNG, DATE);

        assertEquals(LocalDateTime.of(2026, 5, 1, 9, 30), access.arrivalAt().orElseThrow());
    }

    @Test
    void 출발_도착_모두_근교역이_있고_운행하면_AVAILABLE() {
        StubTrainInfoClient stub = new StubTrainInfoClient();
        stub.respond(() -> new TrainAvailability.Available(ktx()));

        TrainAccess access = service(stub).accessTo(SEOUL_LAT, SEOUL_LNG, JEONGSEON_LAT, JEONGSEON_LNG, DATE);

        assertEquals(TrainAccess.Status.AVAILABLE, access.status());
        assertEquals("서울", access.fromStation());
        assertEquals("정선", access.toStation());
        assertEquals(150, access.fastest().durationMinutes());
    }

    @Test
    void 목적지_근교에_역이_없으면_NO_STATION() {
        StubTrainInfoClient stub = new StubTrainInfoClient();
        stub.respond(() -> {
            throw new AssertionError("역이 없으면 열차 조회를 하면 안 된다");
        });

        // 제주 좌표 — 마스터의 어느 육지 역과도 50km 밖
        TrainAccess access = service(stub).accessTo(SEOUL_LAT, SEOUL_LNG, 33.4996, 126.5312, DATE);

        assertEquals(TrainAccess.Status.NO_STATION, access.status());
        assertEquals("서울", access.fromStation());
        assertEquals(null, access.toStation());
    }

    @Test
    void 역은_있는데_그날_미운행이면_NO_SERVICE_ON_DATE() {
        StubTrainInfoClient stub = new StubTrainInfoClient();
        stub.respond(TrainAvailability.NoServiceOnDate::new);

        TrainAccess access = service(stub).accessTo(SEOUL_LAT, SEOUL_LNG, JEONGSEON_LAT, JEONGSEON_LNG, DATE);

        assertEquals(TrainAccess.Status.NO_SERVICE_ON_DATE, access.status());
        assertEquals("정선", access.toStation());
    }

    @Test
    void 열차_조회_실패는_역명_유지하며_UNAVAILABLE() {
        StubTrainInfoClient stub = new StubTrainInfoClient();
        stub.respond(TrainAvailability.Unavailable::new);

        TrainAccess access = service(stub).accessTo(SEOUL_LAT, SEOUL_LNG, JEONGSEON_LAT, JEONGSEON_LNG, DATE);

        assertEquals(TrainAccess.Status.UNAVAILABLE, access.status());
        assertEquals("서울", access.fromStation());
        assertEquals("정선", access.toStation());
    }
}
