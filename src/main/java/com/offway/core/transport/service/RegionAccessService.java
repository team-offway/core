package com.offway.core.transport.service;

import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.domain.Port;
import com.offway.core.transport.domain.RegionArrival;
import com.offway.core.transport.domain.Terminal;
import com.offway.core.transport.domain.TransitMode;
import com.offway.core.transport.domain.TransportMode;
import com.offway.core.transport.service.dto.RegionAccess;
import com.offway.core.transport.service.dto.TransitOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 지역까지의 대중교통 접근 조회(#97) — transport 가 itinerary(코스)에 노출하는 공개 서비스. 열차 하나만 보던 것을
 * 버스·여객선까지 넓힌 자리다.
 *
 * <p><b>왜 필요했나.</b> 코스는 "내린 곳" 을 지역 안 동선의 기준점으로 쓴다(#127). 그런데 그 지점을 열차역에서만
 * 찾아, 역이 없는 지역은 <b>출발지 좌표</b>로 되돌아갔다 — 서울에서 출발하면 "완도 장소들 중 서울에서 가까운 곳"
 * 부터 이어붙는 동선이 나온다. 버스 터미널 789곳·항구 500곳이 이미 시드돼 있는데도 코스가 쓰지 않던 상태였다.
 *
 * <p><b>외부 호출을 하지 않는다.</b> 터미널·항구 해석은 시드를 인메모리로 들고 하는 좌표 최근접이고, 구간
 * 소요시간은 DB 에서 읽는다. 값이 없으면 자리만 만들어 두고 배치가 나중에 채운다
 * ({@link TransitDurationRefreshService}) — 요청 경로에서 외부 I/O 를 빼는 규칙 그대로다.
 *
 * <p>도착 지점을 고르는 규칙은 {@link RegionAccess#orNearer} 가 소유한다. 서비스는 후보를 모아 넘길 뿐이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionAccessService {

    /** 여행도 배차도 한국 기준이다 — 서버 기본 시간대에 기대지 않는다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final TrainAccessService trainAccessService;
    private final BusTerminalResolver busTerminalResolver;
    private final FerryPortResolver ferryPortResolver;
    private final TransitDurationService transitDurationService;
    private final TravelTimeProvider travelTimeProvider;

    /**
     * 출발 좌표에서 목적지 좌표(지역)까지, 해당 날짜의 대중교통 접근.
     *
     * @param notBefore 집을 나서는 시각 — 이 시각 이후에 떠나는 편만 고른다(#138)
     */
    public RegionAccess accessTo(
            double originLat, double originLng, double destLat, double destLng, LocalDate date, LocalTime notBefore) {
        RegionAccess train = trainAccessService.accessTo(originLat, originLng, destLat, destLng, date, notBefore);
        Optional<Terminal> destTerminal = busTerminalResolver.nearest(destLat, destLng);
        Optional<Port> destPort = ferryPortResolver.nearest(destLat, destLng);

        RegionAccess chosen = train.orNearer(
                new Coordinate(destLat, destLng),
                destTerminal.map(RegionArrival::of).orElse(null),
                destPort.map(RegionArrival::of).orElse(null));
        if (chosen != train) {
            // 열차만 보던 시절 이 지역은 도착 지점을 몰라 출발지로 되돌아갔다. 무엇이 그 자리를 채웠는지 남긴다.
            log.debug("도착 지점을 {}(으)로 잡습니다 — 열차 상태={} 지점={}",
                    chosen.mode().label(), train.status(), chosen.toName());
            chosen = chosen.withDuration(
                    durationOf(chosen.mode(), originLat, originLng, destTerminal, destPort).orElse(null));
        }
        return chosen.withDistanceKm(distanceKm(new Coordinate(originLat, originLng), chosen.arrivalPoint()))
                .withAlternatives(alternativesTo(chosen, train, destTerminal, destPort));
    }

    /**
     * 자차로 지역까지(#379). 도착 지점이 <b>지역 그 자체</b>라 역·터미널을 해석할 것이 없다.
     *
     * <p>예전에는 자차 코스에 이 값을 아예 만들지 않아 화면의 교통 카드가 통째로 비었다. 그런데 <b>같은
     * 계산을 이미 하고 있었다</b> — 후보지역 추천이 그 지역까지의 도달시간을 답하고, 사용자는 그 숫자를 보고
     * 지역을 골랐다. 코스 상세에서만 그 값이 사라지던 셈이다.
     *
     * <p>이동시간 조회는 캐시를 탄다. 같은 (출발지, 지역) 조합이면 코스 안에서 다시 부르지 않는다.
     *
     * @param originName 출발지 표시명(#382) — 서버가 좌표에서 만들 수 없어 저장된 값을 그대로 받는다.
     *     모르면 null 이고 화면은 그 조각만 접는다
     */
    public RegionAccess carAccessTo(
            String originName, String regionName, double originLat, double originLng, double destLat, double destLng) {
        Coordinate origin = new Coordinate(originLat, originLng);
        Coordinate destination = new Coordinate(destLat, destLng);
        int minutes = travelTimeProvider.reachMinutes(origin, destination, TransportMode.CAR);
        return RegionAccess.car(
                originName, regionName, destination, minutes, distanceKm(origin, Optional.of(destination)));
    }

    /**
     * 출발지에서 도착 지점까지의 거리(#379) — 지점을 못 찾았으면 빈 값이다.
     *
     * <p><b>직선거리다.</b> 실제 주행거리를 알려면 경로 조회가 한 건 더 나가는데, 화면이 소요시간 옆에 붙이는
     * 곁가지 값 하나 때문에 외부 한도를 태울 이유가 없다. 이미 도착 지점을 고를 때 재고 있는 계산이다.
     */
    private static Integer distanceKm(Coordinate origin, Optional<Coordinate> arrival) {
        return arrival.map(point -> (int) Math.round(origin.haversineKmTo(point))).orElse(null);
    }

    /**
     * 대표 말고 이 지역에 닿는 다른 수단들(#97).
     *
     * <p>대표 하나만 내리면, 배로도 갈 수 있다는 걸 아는 사용자에게는 화면이 틀린 것으로 읽힌다. 반대로
     * <b>해석되지 않은 수단은 넣지 않는다</b> — "없는 선택지" 를 늘어놓는 것은 정보가 아니라 소음이다.
     *
     * <p>소요시간은 여기서 새로 재지 않는다. 대안까지 구간을 물으면 요청 하나에 조회가 수단 수만큼 늘고,
     * 그 값을 화면이 실제로 쓰는지도 아직 모른다. 대표가 가진 값만 그대로 옮긴다.
     */
    private static List<TransitOption> alternativesTo(
            RegionAccess chosen, RegionAccess train, Optional<Terminal> destTerminal, Optional<Port> destPort) {
        List<TransitOption> others = new ArrayList<>();
        if (chosen.mode() != TransitMode.TRAIN && train.toName() != null) {
            others.add(new TransitOption(TransitMode.TRAIN, train.toName(), trainMinutes(train)));
        }
        destTerminal
                .filter(terminal -> TransitMode.of(terminal.kind()) != chosen.mode())
                .ifPresent(terminal ->
                        others.add(new TransitOption(TransitMode.of(terminal.kind()), terminal.name(), null)));
        destPort
                .filter(port -> chosen.mode() != TransitMode.FERRY)
                .ifPresent(port -> others.add(new TransitOption(TransitMode.FERRY, port.name(), null)));
        return List.copyOf(others);
    }

    private static Integer trainMinutes(RegionAccess train) {
        return train.fastest() == null ? null : train.fastest().durationMinutes();
    }

    /**
     * 저장해 둔 구간 소요시간(#107). 없으면 자리만 만들어지고 이번 응답에는 안 실린다.
     *
     * <p><b>출발 지점은 도착과 같은 종류로 푼다.</b> 고속({@code NAEK...})과 시외({@code NAI...})는 코드
     * 공간이 겹치지 않아, 섞어 물으면 제공기관이 알 수 없는 코드로 읽는다.
     *
     * <p>여객선은 출발 항구가 {@link FerryPortResolver} 반경 안에 있어야 한다. 서울에서 울릉도는 여기서
     * 빈 값이 되는데 <b>맞는 답이다</b> — 서울에는 항구가 없고, 실제로는 포항까지 육상으로 간 뒤 배를 탄다.
     * 그 환승을 잇는 것은 이 함수의 일이 아니다.
     */
    private Optional<Integer> durationOf(
            TransitMode mode, double originLat, double originLng,
            Optional<Terminal> destTerminal, Optional<Port> destPort) {
        LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
        return switch (mode) {
            case EXPRESS_BUS, INTERCITY_BUS -> destTerminal.flatMap(arrival ->
                    busTerminalResolver.nearest(originLat, originLng, arrival.kind())
                            .flatMap(departure -> transitDurationService.minutesFor(
                                    mode, departure.code(), arrival.code(), now)));
            case FERRY -> destPort.flatMap(arrival ->
                    ferryPortResolver.nearest(originLat, originLng)
                            .flatMap(departure -> transitDurationService.minutesFor(
                                    mode, departure.code(), arrival.code(), now)));
            // 구간 표를 쓰지 않는 둘. 열차는 실제 시각을 직접 답하고, 자차는 구간이 없다(#379).
            case TRAIN, CAR -> Optional.empty();
        };
    }
}
