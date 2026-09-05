package com.offway.core.transport.service;

import com.offway.core.common.external.ExternalApiCachePolicy;
import java.time.LocalTime;
import com.offway.core.leave.domain.StartDayLeave;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.geo.Coordinate;
import com.offway.core.transport.domain.TrainAvailability;
import com.offway.core.transport.domain.TrainLeg;
import com.offway.core.transport.domain.TrainStation;
import com.offway.core.transport.infrastructure.tago.StubTrainInfoClient;
import com.offway.core.transport.repository.TrainStationRepository;
import com.offway.core.transport.service.dto.RegionAccess;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** TrainAccessService — 좌표 최근접 역해석 + 열차 조회를 조립한 4-way 결과. 역 마스터는 stub 리포지토리로, 열차는 stub 클라이언트로 격리. */
class TrainAccessServiceTest {

    /**
     * 이 클래스가 쓰는 출발 시각 — <b>필터를 끈 값</b>이다.
     *
     * <p>여기서 보는 것은 조회 결과를 어떤 상태로 매핑하는가(역 없음·미운행·조회 실패)다. 출발 시각 필터까지
     * 함께 걸면 픽스처 열차의 출발 시각을 바꿀 때마다 관계없는 테스트가 깨진다. 필터 자체는
     * {@link #출발_시각_이후_편이_없으면_미운행으로_답한다()} 가 따로 본다.
     */
    private static final LocalTime DEPART_AT = LocalTime.MIN;

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

    // ── #435 후보 역 픽스처 — 강변역 주변 실좌표(시드와 동일)
    /** 강변역 — 이 좌표에서 수서 5.80㎞ · 왕십리 6.02㎞ · 청량리 6.71㎞ 순이다. */
    private static final double GANGBYEON_LAT = 37.5350;
    private static final double GANGBYEON_LNG = 127.0946;
    private static final double JECHEON_LAT = 37.1285;
    private static final double JECHEON_LNG = 128.205339;
    private static final String SUSEO = "NATH30000";
    private static final String WANGSIMNI = "NAT130104";

    /** 강변역 주변 세 역 + 목적지 제천. 순서를 섞어 둔다 — 거리로 정렬하는지 보려는 것이지 입력 순서가 아니다. */
    private static final List<TrainStation> METRO_MASTER = List.of(
            TrainStation.of("NAT130126", "청량리", 37.580787, 127.044959),
            TrainStation.of(SUSEO, "수서", 37.483661, 127.106067),
            TrainStation.of(WANGSIMNI, "왕십리", 37.561428, 127.034960),
            TrainStation.of("NAT021549", "제천", JECHEON_LAT, JECHEON_LNG));

    private static TrainAccessService service(StubTrainInfoClient stub) {
        return service(stub, MASTER);
    }

    private static TrainAccessService service(StubTrainInfoClient stub, List<TrainStation> master) {
        TrainStationRepository repo = () -> master; // findAll 단일 메서드 → 람다
        return new TrainAccessService(new TrainStationResolver(repo), new TrainRouteService(stub, ExternalApiCachePolicy.ALWAYS_CACHE));
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

        RegionAccess access = service(stub).accessTo(SEOUL_LAT, SEOUL_LNG, JEONGSEON_LAT, JEONGSEON_LNG, DATE, DEPART_AT);

        assertEquals(new Coordinate(JEONGSEON_LAT, JEONGSEON_LNG), access.arrivalPoint().orElseThrow());
        assertTrue(access.arrivalAt().isEmpty(), "운행을 못 찾았으면 도착 시각은 모른다");
    }

    @Test
    void 조회가_실패해도_도착역_좌표는_그대로_준다() {
        StubTrainInfoClient stub = new StubTrainInfoClient();
        stub.respond(TrainAvailability.Unavailable::new);

        RegionAccess access = service(stub).accessTo(SEOUL_LAT, SEOUL_LNG, JEONGSEON_LAT, JEONGSEON_LNG, DATE, DEPART_AT);

        assertEquals(new Coordinate(JEONGSEON_LAT, JEONGSEON_LNG), access.arrivalPoint().orElseThrow());
    }

    @Test
    void 역이_없으면_도착_지점도_없다() {
        // 태평양 한가운데 — 50㎞ 안에 역이 없다. 이때만 도착 지점이 비어 코스가 출발지 기준으로 돌아간다.
        StubTrainInfoClient stub = new StubTrainInfoClient();

        RegionAccess access = service(stub).accessTo(SEOUL_LAT, SEOUL_LNG, 20.0, 150.0, DATE, DEPART_AT);

        assertEquals(RegionAccess.Status.NO_STATION, access.status());
        assertTrue(access.arrivalPoint().isEmpty());
    }

    @Test
    void 운행하면_도착_시각을_준다() {
        StubTrainInfoClient stub = new StubTrainInfoClient();
        stub.respond(() -> new TrainAvailability.Available(List.of(ktx())));

        RegionAccess access = service(stub).accessTo(SEOUL_LAT, SEOUL_LNG, JEONGSEON_LAT, JEONGSEON_LNG, DATE, DEPART_AT);

        assertEquals(LocalDateTime.of(2026, 5, 1, 9, 30), access.arrivalAt().orElseThrow());
    }

    @Test
    void 출발_도착_모두_근교역이_있고_운행하면_AVAILABLE() {
        StubTrainInfoClient stub = new StubTrainInfoClient();
        stub.respond(() -> new TrainAvailability.Available(List.of(ktx())));

        RegionAccess access = service(stub).accessTo(SEOUL_LAT, SEOUL_LNG, JEONGSEON_LAT, JEONGSEON_LNG, DATE, DEPART_AT);

        assertEquals(RegionAccess.Status.AVAILABLE, access.status());
        assertEquals("서울", access.fromName());
        assertEquals("정선", access.toName());
        assertEquals(150, access.fastest().durationMinutes());
    }

    @Test
    void 목적지_근교에_역이_없으면_NO_STATION() {
        StubTrainInfoClient stub = new StubTrainInfoClient();
        stub.respond(() -> {
            throw new AssertionError("역이 없으면 열차 조회를 하면 안 된다");
        });

        // 제주 좌표 — 마스터의 어느 육지 역과도 50km 밖
        RegionAccess access = service(stub).accessTo(SEOUL_LAT, SEOUL_LNG, 33.4996, 126.5312, DATE, DEPART_AT);

        assertEquals(RegionAccess.Status.NO_STATION, access.status());
        assertEquals("서울", access.fromName());
        assertEquals(null, access.toName());
    }

    @Test
    void 역은_있는데_그날_미운행이면_NO_SERVICE_ON_DATE() {
        StubTrainInfoClient stub = new StubTrainInfoClient();
        stub.respond(TrainAvailability.NoServiceOnDate::new);

        RegionAccess access = service(stub).accessTo(SEOUL_LAT, SEOUL_LNG, JEONGSEON_LAT, JEONGSEON_LNG, DATE, DEPART_AT);

        assertEquals(RegionAccess.Status.NO_SERVICE_ON_DATE, access.status());
        assertEquals("정선", access.toName());
    }

    @Test
    void 열차_조회_실패는_역명_유지하며_UNAVAILABLE() {
        StubTrainInfoClient stub = new StubTrainInfoClient();
        stub.respond(TrainAvailability.Unavailable::new);

        RegionAccess access = service(stub).accessTo(SEOUL_LAT, SEOUL_LNG, JEONGSEON_LAT, JEONGSEON_LNG, DATE, DEPART_AT);

        assertEquals(RegionAccess.Status.UNAVAILABLE, access.status());
        assertEquals("서울", access.fromName());
        assertEquals("정선", access.toName());
    }

    @Test
    void 출발_시각_이후_편이_없으면_미운행으로_답한다() {
        // 반반차로 15시에 나서는데 그 지역 막차가 이미 지났다. "가장 빠른 편"(아침 KTX)을 답하면 지킬 수 없는
        // 코스가 된다 — 사용자에게는 그날 열차가 없는 것과 같은 결과라 같은 상태로 답한다.
        StubTrainInfoClient stub = new StubTrainInfoClient();
        stub.respond(() -> new TrainAvailability.Available(List.of(ktx()))); // 07:00 출발

        RegionAccess access = service(stub)
                .accessTo(
                        SEOUL_LAT,
                        SEOUL_LNG,
                        JEONGSEON_LAT,
                        JEONGSEON_LNG,
                        DATE,
                        StartDayLeave.QUARTER_DAY.departureTime());

        assertEquals(RegionAccess.Status.NO_SERVICE_ON_DATE, access.status());
        assertTrue(access.arrivalAt().isEmpty(), "탈 수 없는 편의 도착 시각을 주면 안 된다");
    }

    @Test
    void 출발_시각_이후_편이_있으면_그_편을_준다() {
        // 필터가 "전부 거절" 이 아님을 보인다 — 종일(08시)에는 09시 편이 잡혀야 한다.
        StubTrainInfoClient stub = new StubTrainInfoClient();
        stub.respond(() -> new TrainAvailability.Available(List.of(
                ktx(), // 07:00 출발 — 종일 기준으로도 걸러진다
                TrainLeg.of("KTX", LocalDateTime.of(2026, 5, 1, 9, 0), LocalDateTime.of(2026, 5, 1, 11, 30)))));

        RegionAccess access = service(stub)
                .accessTo(
                        SEOUL_LAT, SEOUL_LNG, JEONGSEON_LAT, JEONGSEON_LNG, DATE,
                        StartDayLeave.FULL_DAY.departureTime());

        assertEquals(LocalDateTime.of(2026, 5, 1, 11, 30), access.arrivalAt().orElseThrow());
    }

    @Test
    void 늦게_떠나도_빨리_닿는_편을_고른다() {
        // 정렬 기준은 출발 시각이 아니라 소요시간이다 — 첫날을 더 남기는 쪽이 낫다.
        StubTrainInfoClient stub = new StubTrainInfoClient();
        stub.respond(() -> new TrainAvailability.Available(List.of(
                TrainLeg.of("무궁화", LocalDateTime.of(2026, 5, 1, 9, 0), LocalDateTime.of(2026, 5, 1, 14, 0)),
                TrainLeg.of("KTX", LocalDateTime.of(2026, 5, 1, 11, 0), LocalDateTime.of(2026, 5, 1, 13, 0)))));

        RegionAccess access = service(stub)
                .accessTo(
                        SEOUL_LAT, SEOUL_LNG, JEONGSEON_LAT, JEONGSEON_LNG, DATE,
                        StartDayLeave.FULL_DAY.departureTime());

        assertEquals(LocalDateTime.of(2026, 5, 1, 13, 0), access.arrivalAt().orElseThrow());
    }

    /**
     * 최근접 역에 그 방면 열차가 없으면 다음 후보로 넘어간다(#435).
     *
     * <p>실제로 겪은 상황이다 — 강변역에서 제천에 갈 때 최근접은 수서(5.80㎞)인데 SRT 전용이라 제천행이
     * 없고, 제천행이 서는 왕십리는 6.02㎞ 다. <b>0.22㎞ 차이로</b> 밀려서 예전에는 탈 수 있는 열차가
     * 통째로 사라졌다.
     */
    @Test
    void 최근접_역에_운행이_없으면_다음_후보_역을_쓴다() {
        StubTrainInfoClient stub = new StubTrainInfoClient();
        stub.respondByDeparture(departure -> SUSEO.equals(departure)
                ? new TrainAvailability.NoServiceOnDate()
                : new TrainAvailability.Available(List.of(ktx())));

        RegionAccess access = service(stub, METRO_MASTER)
                .accessTo(GANGBYEON_LAT, GANGBYEON_LNG, JECHEON_LAT, JECHEON_LNG, DATE, DEPART_AT);

        assertEquals(RegionAccess.Status.AVAILABLE, access.status());
        // 화면에 뜨는 출발역도 실제로 조회한 역이라야 한다 — 수서라고 적어 두면 거기 가서 못 탄다.
        assertEquals("왕십리", access.fromName());
        assertTrue(access.arrivalAt().isPresent(), "운행을 찾았으면 도착 시각을 안다");
    }

    /**
     * 후보를 다 물어도 없으면 <b>최근접 역이 답한 사유</b>를 돌려준다.
     *
     * <p>더 먼 역의 사유로 바꾸지 않는다 — 사용자에게 가장 가까운 역이 기준이고, 로그로 원인을 따라갈 때도
     * 그쪽이 자연스럽다.
     */
    @Test
    void 후보를_다_물어도_없으면_최근접_역의_사유를_준다() {
        StubTrainInfoClient stub = new StubTrainInfoClient();
        stub.respond(TrainAvailability.NoServiceOnDate::new);

        RegionAccess access = service(stub, METRO_MASTER)
                .accessTo(GANGBYEON_LAT, GANGBYEON_LNG, JECHEON_LAT, JECHEON_LNG, DATE, DEPART_AT);

        assertEquals(RegionAccess.Status.NO_SERVICE_ON_DATE, access.status());
        assertEquals("수서", access.fromName());
        // 도착 지점은 조회 결과와 무관하게 남는다 — 코스 동선의 기준점이라 여기서 잃으면 안 된다(#127).
        assertEquals(new Coordinate(JECHEON_LAT, JECHEON_LNG), access.arrivalPoint().orElseThrow());
    }

    /**
     * 조회가 실패하면 다음 후보를 <b>묻지 않는다</b>.
     *
     * <p>외부 장애라 다른 역을 물어도 같은 실패가 돌아오는데, 조회 하나가 최대 6초다. 후보 수만큼 곱하면
     * 코스 응답이 그만큼 늦어진다 — 얻는 것 없이 사용자만 기다린다.
     */
    @Test
    void 조회가_실패하면_다음_후보를_묻지_않는다() {
        AtomicInteger calls = new AtomicInteger();
        StubTrainInfoClient stub = new StubTrainInfoClient();
        stub.respondByDeparture(departure -> {
            calls.incrementAndGet();
            return new TrainAvailability.Unavailable();
        });

        RegionAccess access = service(stub, METRO_MASTER)
                .accessTo(GANGBYEON_LAT, GANGBYEON_LNG, JECHEON_LAT, JECHEON_LNG, DATE, DEPART_AT);

        assertEquals(RegionAccess.Status.UNAVAILABLE, access.status());
        assertEquals(1, calls.get(), "외부 실패 뒤에도 후보를 계속 물으면 응답만 느려진다");
    }
}
