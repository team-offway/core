package com.offway.core.transport.service;

import com.offway.core.common.geo.Coordinate;
import com.offway.core.transport.domain.Port;
import com.offway.core.transport.domain.RegionArrival;
import com.offway.core.transport.domain.Terminal;
import com.offway.core.transport.domain.TransferHub;
import com.offway.core.transport.domain.TransitMode;
import com.offway.core.transport.domain.TransportMode;
import com.offway.core.transport.service.dto.RegionAccess;
import com.offway.core.transport.service.dto.TransitOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import com.offway.core.transport.domain.BusTerminalKind;
import com.offway.core.transport.domain.Departure;
import java.util.Optional;
import java.util.stream.Stream;
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
    /**
     * 출발 후보를 몇 개까지 훑나(#507).
     *
     * <p>서울처럼 터미널이 몰린 곳은 반경 30㎞ 안에 열 곳이 넘는다. 후보마다 DB 를 한 번 보므로 상한을
     * 둔다 — 같은 자리의 중복 코드를 가르는 것이 목적이라, 우선순위 위쪽 몇 개면 충분하다.
     */
    /**
     * 환승 대기로 얹는 시간(#508).
     *
     * <p>두 구간의 시간표를 이을 수 없어 실제 대기를 모른다. 합만 보여주면 실제보다 짧게 말하게 되는데,
     * 코스는 그 숫자로 첫날 일정을 자른다 — <b>모자라게 말하면 지킬 수 없는 코스가 된다.</b>
     */
    private static final int TRANSFER_BUFFER_MINUTES = 40;

    private static final int MAX_DEPARTURE_CANDIDATES = 8;

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final TrainAccessService trainAccessService;
    private final BusTerminalResolver busTerminalResolver;
    private final FerryPortResolver ferryPortResolver;
    private final TransitDurationService transitDurationService;
    private final TransitDepartureService transitDepartureService;
    private final TravelTimeProvider travelTimeProvider;

    /**
     * 출발 좌표에서 목적지 좌표(지역)까지, 해당 날짜의 대중교통 접근.
     *
     * @param plannedDeparture 집을 나서기로 한 시각 — 이 시각 이후에 떠나는 편만 고른다(#138).
     *     <b>오늘 코스면 지금 시각이 바닥이 된다</b>(#422)
     */
    public RegionAccess accessTo(
            double originLat, double originLng, double destLat, double destLng, LocalDate date, LocalTime plannedDeparture) {
        return accessTo(originLat, originLng, destLat, destLng, date, plannedDeparture, null);
    }

    /**
     * 수단을 지정한 접근 조회(#453) — "이 수단으로 가면 어떻게 되나".
     *
     * <p>사용자가 카드에서 수단 칩을 누르면 그 수단으로 다시 묻는다. <b>도착 지점이 바뀌면 코스도 바뀌어야
     * 한다</b> — 코스는 집이 아니라 내린 곳에서 시작하기 때문이다(#127). 지역에 따라 역과 터미널이 수십 ㎞
     * 떨어져 있어(양양은 강릉역까지 42㎞), 시간표만 갈아끼우면 동선이 그대로 남아 어긋난다.
     *
     * <p><b>없는 수단을 요청하면 자동 선택으로 돌아간다.</b> 그 지역에 그 수단이 안 닿는데 억지로 세우면
     * 도착 지점이 없는 코스가 된다.
     *
     * @param preferred 이 수단으로 고정한다. {@code null} 이면 지금까지처럼 자동으로 고른다
     */
    public RegionAccess accessTo(
            double originLat, double originLng, double destLat, double destLng, LocalDate date,
            LocalTime plannedDeparture, TransitMode preferred) {
        // **여기 한 곳에서 바닥을 정한다**(#422). 오늘 코스면 계획 시각이 이미 지났을 수 있어, 그대로
        // 쓰면 못 타는 차가 목록 맨 위에 뜬다. 아래 열차·버스·여객선이 전부 이 값을 쓴다.
        LocalTime notBefore = Departure.boardableFrom(date, plannedDeparture, LocalDateTime.now(SERVICE_ZONE));
        RegionAccess train = trainAccessService.accessTo(originLat, originLng, destLat, destLng, date, notBefore);
        // **고속·시외를 둘 다 푼다**(#493). 예전에는 종류를 안 가린 최근접 하나만 풀어, 둘이 한 자리를
        // 두고 경쟁했다. 종합터미널은 좌표가 같아 늘 시외가 이겼고 — 그 판단은 맞다(군 단위는 시외가
        // 촘촘히 닿는다) — 진 쪽은 대안에도 안 남아 89곳 중 68곳에서 고속버스가 통째로 사라졌다.
        // 고정 요청(#453)도 같은 이유로 무시됐다.
        Optional<Terminal> destExpress = busTerminalResolver.nearest(destLat, destLng, BusTerminalKind.EXPRESS);
        Optional<Terminal> destIntercity = busTerminalResolver.nearest(destLat, destLng, BusTerminalKind.INTERCITY);
        // 대표 후보는 지금까지처럼 종류를 안 가린 최근접이다 — 뽑는 규칙은 안 건드린다(#463).
        Optional<Terminal> destTerminal = busTerminalResolver.nearest(destLat, destLng);
        Optional<Port> destPort = ferryPortResolver.nearest(destLat, destLng);

        RegionAccess chosen = forcedTo(preferred, train, destExpress, destIntercity, destPort)
                .orElseGet(() -> train.orNearer(
                        new Coordinate(destLat, destLng),
                        boardable(originLat, originLng, destTerminal, destPort)));
        if (chosen != train) {
            // 열차만 보던 시절 이 지역은 도착 지점을 몰라 출발지로 되돌아갔다. 무엇이 그 자리를 채웠는지 남긴다.
            log.debug("도착 지점을 {}(으)로 잡습니다 — 열차 상태={} 지점={}",
                    chosen.mode().label(), train.status(), chosen.toName());
            // 출발 지점명을 함께 싣는다(#396). 서버는 이미 이 지점을 찾고 있었는데 조회에만 쓰고
            // 이름을 버려, 버스·여객선 카드만 "어디서 타는지" 가 빈 채로 나갔다.
            // 대표가 어느 종류든 **그 종류의 터미널**로 채운다. 고정 요청이 고속을 세웠는데 시외 터미널로
            // 출발지·구간을 풀면 제공기관이 알 수 없는 코드로 읽는다(코드 공간이 겹치지 않는다).
            Optional<Terminal> chosenTerminal = terminalFor(chosen.mode(), destExpress, destIntercity);
            // **출발 지점을 한 번만 푼다.** 셋이 각자 풀면 그 사이 배치가 한 코드를 미운행으로 적었을 때
            // 출발지명은 A 터미널, 소요시간·시간표는 B 터미널인 응답이 나간다(#507 리뷰).
            Optional<RegionArrival> departure =
                    departurePoint(chosen.mode(), originLat, originLng, chosenTerminal, destPort);
            chosen = chosen
                    .withFromName(departure.map(RegionArrival::name).orElse(null))
                    .withDuration(durationOf(chosen.mode(), departure, chosenTerminal, destPort).orElse(null))
                    .withDepartures(departuresOf(
                            chosen.mode(), departure, chosenTerminal, destPort, date, notBefore));
            chosen = viaOrHonest(chosen, departure, chosenTerminal);
        }
        return chosen.withDistanceKm(distanceKm(new Coordinate(originLat, originLng), chosen.arrivalPoint()))
                .withAlternatives(alternativesTo(
                        chosen, train, destExpress, destIntercity, destPort, originLat, originLng, date, notBefore));
    }

    /**
     * 대표 후보 중 <b>어디서 타는지 말할 수 있는 것</b>만(#454).
     *
     * <p>대표는 지역에 가장 가까운 도착 지점으로 골랐는데, 그 판정에 출발 쪽이 안 들어갔다. 그래서 서울에서
     * 완도·하동을 물으면 <b>여객선이 대표가 되고 출발 지점이 빈다</b> — 서울에 항구가 없기 때문이다. 사용자에게는
     * "배로 가세요, 어디서 타는지는 모릅니다" 가 된다. 두 곳 다 시외버스 터미널이 지역명 그대로 잡혀 있는데도 그랬다.
     *
     * <p>도착 지점이 조금 더 가깝다고 해서 탈 곳을 모르는 수단을 앞세울 이유가 없다. 그래서 후보에서 뺀다.
     *
     * <p><b>다 빠지면 도로 넣는다.</b> 울릉군은 육상 수단이 아예 없어 여객선이 대표인 것이 맞고, 그때는 출발
     * 지점이 비는 것도 맞는 답이다(포항까지 육상으로 간 뒤 배를 탄다 — 그 환승을 잇는 것은 별개 작업이다).
     * 여기서 마저 빼면 도착 지점을 아는 수단이 있는데도 "못 간다" 가 된다.
     *
     * <p><b>대안 목록은 안 건드린다.</b> 배로도 갈 수 있다는 사실 자체는 맞다 — 대표로 앞세우지 않을 뿐이다.
     */
    private RegionArrival[] boardable(
            double originLat, double originLng, Optional<Terminal> destTerminal, Optional<Port> destPort) {
        List<RegionArrival> all = Stream.of(
                        destTerminal.map(RegionArrival::of), destPort.map(RegionArrival::of))
                .flatMap(Optional::stream)
                .toList();
        List<RegionArrival> known = all.stream()
                .filter(arrival -> departurePoint(
                        arrival.mode(), originLat, originLng, destTerminal, destPort).isPresent())
                .toList();
        if (known.isEmpty()) {
            return all.toArray(RegionArrival[]::new);
        }
        if (known.size() < all.size()) {
            log.debug("출발 지점을 못 찾아 대표 후보에서 뺍니다 — 남은 후보={}",
                    known.stream().map(arrival -> arrival.mode().label()).toList());
        }
        return known.toArray(RegionArrival[]::new);
    }

    /**
     * 요청받은 수단으로 고정한 결과(#453). 그 수단이 이 지역에 안 닿으면 빈 값 — 호출자가 자동 선택으로 돌아간다.
     *
     * <p>열차는 이미 조회한 결과를 그대로 쓴다. 버스·여객선은 도착 지점만 아는 상태({@code POINT_ONLY})로
     * 만드는데, 그 뒤 호출자가 출발 지점·소요시간·시간표를 채우는 흐름이 자동 선택과 같다.
     *
     * <p><b>"안 닿는다" 의 기준은 상태가 아니라 도착 지점이다.</b> 열차는 조회 결과가 어떻든 값이 나오므로,
     * 상태로 가르면 역이 아예 없는 지역({@code NO_STATION})까지 열차로 고정된다 — 내리는 곳을 모르는 채
     * 동선을 짜게 되고, 코스가 출발지 기준으로 되돌아간다. 그날 운행이 없는 것({@code NO_SERVICE_ON_DATE})은
     * 지점을 알므로 그대로 열차로 답한다. 버스·여객선은 지점이 있어야 값이 만들어져 이 검사가 따로 필요 없다.
     */
    private static Optional<RegionAccess> forcedTo(
            TransitMode preferred, RegionAccess train,
            Optional<Terminal> destExpress, Optional<Terminal> destIntercity, Optional<Port> destPort) {
        if (preferred == null) {
            return Optional.empty();
        }
        return switch (preferred) {
            case TRAIN -> Optional.of(train).filter(access -> access.arrivalPoint().isPresent());
            // **그 종류의 터미널을 직접 본다**(#493). 예전에는 종류를 안 가린 최근접 하나를 받아 종류가
            // 맞는지 걸렀는데, 종합터미널은 좌표가 같아 늘 시외가 그 자리를 차지했다. 그래서 고속으로
            // 고정 요청해도 시외가 나갔다 — 사용자가 고른 수단이 조용히 무시된 것이다.
            case EXPRESS_BUS -> destExpress.map(RegionArrival::of).map(RegionAccess::pointOnly);
            case INTERCITY_BUS -> destIntercity.map(RegionArrival::of).map(RegionAccess::pointOnly);
            case FERRY -> destPort.map(RegionArrival::of).map(RegionAccess::pointOnly);
            // 자차는 이 경로로 오지 않는다 — carAccessTo 가 따로 있다.
            case CAR -> Optional.empty();
        };
    }

    /** 이 수단이 쓰는 도착 터미널. 버스가 아니면 빈 값이다. */
    private static Optional<Terminal> terminalFor(
            TransitMode mode, Optional<Terminal> destExpress, Optional<Terminal> destIntercity) {
        return switch (mode) {
            case EXPRESS_BUS -> destExpress;
            case INTERCITY_BUS -> destIntercity;
            case TRAIN, FERRY, CAR -> Optional.empty();
        };
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
     *
     * <p><b>시간표는 다르다</b>(#414). "무엇으로, 어디에, 몇 분" 만으로는 대안을 고를 수 없다 — 시외버스가
     * 40분 더 걸려도 지금 바로 타는 편이 있으면 그쪽을 고른다. 그래서 대안에도 붙인다.
     *
     * <p>대신 <b>조회창이 그 비용을 막는다</b>. 여행일이 창 밖이면 어느 수단도 안 묻고(대부분의 코스가
     * 그렇다), 창 안이어도 열차는 이미 받아 둔 하루치에서 고르므로 공짜다. 실제로 느는 것은 버스·여객선
     * 대안뿐이고, 이 지역에 닿는 수단이 셋을 넘지 않아 <b>코스 하나에 최대 3건</b>이다.
     */
    private List<TransitOption> alternativesTo(
            RegionAccess chosen, RegionAccess train,
            Optional<Terminal> destExpress, Optional<Terminal> destIntercity, Optional<Port> destPort,
            double originLat, double originLng, LocalDate date, LocalTime notBefore) {
        List<TransitOption> others = new ArrayList<>();
        if (chosen.mode() != TransitMode.TRAIN && train.toName() != null) {
            others.add(TransitOption.builder()
                    .mode(TransitMode.TRAIN)
                    .toName(train.toName())
                    .durationMinutes(trainMinutes(train))
                    // 열차 시간표는 이미 대표 계산에서 받아 둔 하루치에 있다 — 호출이 늘지 않는다.
                    .departures(train.departures())
                    .build());
        }
        // **고속·시외를 둘 다 싣는다**(#493). 대표로 진 쪽이 대안에서도 빠지면 그 수단은 화면에서
        // 통째로 사라진다 — 89곳 중 68곳에서 고속버스가 그랬다. 둘은 같은 곳에 서더라도 다른 노선망이다.
        Stream.of(destExpress, destIntercity)
                .flatMap(Optional::stream)
                .filter(terminal -> TransitMode.of(terminal.kind()) != chosen.mode())
                .forEach(terminal -> {
                    TransitMode mode = TransitMode.of(terminal.kind());
                    Optional<Terminal> only = Optional.of(terminal);
                    others.add(TransitOption.builder()
                            .mode(mode)
                            .toName(terminal.name())
                            .departures(departuresOf(
                                    mode,
                                    departurePoint(mode, originLat, originLng, only, destPort),
                                    only, destPort, date, notBefore))
                            .build());
                });
        destPort
                .filter(port -> chosen.mode() != TransitMode.FERRY)
                .ifPresent(port -> others.add(TransitOption.builder()
                        .mode(TransitMode.FERRY)
                        .toName(port.name())
                        // 여객선은 터미널을 안 쓴다 — 출발은 항구, 도착 코드도 항구다.
                        .departures(departuresOf(
                                TransitMode.FERRY,
                                departurePoint(TransitMode.FERRY, originLat, originLng, Optional.empty(), destPort),
                                Optional.empty(), destPort, date, notBefore))
                        .build()));
        return List.copyOf(others);
    }

    private static Integer trainMinutes(RegionAccess train) {
        return train.chosen() == null ? null : train.chosen().durationMinutes();
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
            TransitMode mode, Optional<RegionArrival> departure,
            Optional<Terminal> destTerminal, Optional<Port> destPort) {
        LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
        return departure
                .flatMap(from -> arrivalCode(mode, destTerminal, destPort)
                        .flatMap(toCode -> transitDurationService.minutesFor(mode, from.code(), toCode, now)));
    }

    /**
     * 이 수단으로 탈 때 <b>출발 쪽</b> 지점(#396).
     *
     * <p>예전에는 소요시간과 시간표가 각자 같은 해석을 했다. 셋째(출발 지점명)를 붙이면서 한 자리로
     * 모았다 — 세 벌이 갈리면 <b>카드에 뜨는 이름과 실제로 조회한 구간이 어긋난다.</b>
     *
     * <p>버스는 <b>도착 터미널과 같은 종류</b>로 찾는다. 고속·시외는 코드 공간이 겹치지 않아, 섞어
     * 물으면 제공기관이 알 수 없는 코드로 읽는다.
     *
     * <p>여객선은 출발 항구가 반경 안에 있어야 한다. 서울에서 울릉도는 여기서 빈 값이 되는데
     * <b>맞는 답이다</b> — 서울에는 항구가 없다.
     */
    private Optional<RegionArrival> departurePoint(
            TransitMode mode, double originLat, double originLng,
            Optional<Terminal> destTerminal, Optional<Port> destPort) {
        return switch (mode) {
            case EXPRESS_BUS, INTERCITY_BUS -> destTerminal
                    .flatMap(arrival -> boardableDeparture(mode, originLat, originLng, arrival))
                    .map(RegionArrival::of);
            case FERRY -> destPort.isPresent()
                    ? ferryPortResolver.nearest(originLat, originLng).map(RegionArrival::of)
                    : Optional.empty();
            // 구간 표를 쓰지 않는 둘. 열차는 실제 시각을 직접 답하고, 자차는 구간이 없다(#379).
            case TRAIN, CAR -> Optional.empty();
        };
    }

    /**
     * 직통이 없으면 <b>허브를 한 번 거치는 길</b>을 찾고, 그것도 없으면 없다고 말한다(#508).
     *
     * <p><b>없는 길을 안내하지 않는 것이 이 함수의 전부다.</b> 예전에는 그 구간에 차가 없어도 그냥
     * 답했다 — 서울에서 봉화까지 고속버스로 가라는 안내가 나갔는데 봉화행 노선은 어디에도 없다.
     *
     * <p>손대는 것은 <b>없는 것이 확인된 구간뿐</b>이다. 아직 안 재본 구간은 그대로 둔다 — 모르는 것을
     * 없다고 말하면 멀쩡한 길을 지우게 된다.
     *
     * <p><b>외부를 안 친다.</b> 두 구간 모두 이미 잰 값이 있을 때만 잇는다. 버스 시간표는 오늘~+2일만
     * 답해서 다음 달 코스에는 애초에 실시간 조회가 무의미하고, 여기는 요청 경로다.
     */
    private RegionAccess viaOrHonest(
            RegionAccess chosen, Optional<RegionArrival> departure, Optional<Terminal> destTerminal) {
        Optional<String> depCode = departure.map(RegionArrival::code);
        Optional<String> arrCode = destTerminal.map(Terminal::code);
        if (depCode.isEmpty() || arrCode.isEmpty() || chosen.durationMinutes() != null) {
            return chosen; // 탈 곳을 모르거나, 이미 직통 소요시간을 아는 경우다
        }
        if (!transitDurationService.knownUnroutable(chosen.mode(), depCode.get(), arrCode.get())) {
            return chosen; // 아직 안 재봤다 — 없다고 단정하지 않는다
        }
        return transferVia(chosen.mode(), depCode.get(), arrCode.get())
                .map(via -> chosen.withVia(via.hub().label(), via.totalMinutes()))
                .orElseGet(chosen::withoutRoute);
    }

    /**
     * 두 구간이 <b>모두 이미 잰 값</b>인 허브 중 가장 빠른 것.
     *
     * <p>환승 대기를 {@value #TRANSFER_BUFFER_MINUTES}분 더한다. 두 구간의 시간표를 이을 수 없어 실제
     * 대기를 모르는데, 합만 보여주면 실제보다 짧게 말하게 된다 — <b>모자라게 말하는 쪽이 더 나쁘다.</b>
     */
    private Optional<Transfer> transferVia(TransitMode mode, String depCode, String arrCode) {
        return Arrays.stream(TransferHub.values())
                .flatMap(hub -> hubTerminals(hub, mode).stream()
                        .flatMap(hubTerminal -> transferThrough(mode, depCode, arrCode, hub, hubTerminal).stream()))
                .min(Comparator.comparingInt(Transfer::totalMinutes));
    }

    private Optional<Transfer> transferThrough(
            TransitMode mode, String depCode, String arrCode, TransferHub hub, Terminal hubTerminal) {
        if (hubTerminal.code().equals(depCode) || hubTerminal.code().equals(arrCode)) {
            return Optional.empty(); // 출발·도착이 곧 허브면 경유가 아니다
        }
        return transitDurationService.measuredMinutes(mode, depCode, hubTerminal.code())
                .flatMap(first -> transitDurationService.measuredMinutes(mode, hubTerminal.code(), arrCode)
                        .map(second -> new Transfer(hub, first + second + TRANSFER_BUFFER_MINUTES)));
    }

    /** 허브 자리의 그 수단 터미널 — 코드가 여럿이면 전부 본다(#507). */
    private List<Terminal> hubTerminals(TransferHub hub, TransitMode mode) {
        BusTerminalKind kind = kindOf(mode);
        if (kind == null) {
            return List.of();
        }
        return busTerminalResolver.nearestWithDuplicates(
                hub.coordinate().lat(), hub.coordinate().lng(), kind);
    }

    private static BusTerminalKind kindOf(TransitMode mode) {
        return switch (mode) {
            case EXPRESS_BUS -> BusTerminalKind.EXPRESS;
            case INTERCITY_BUS -> BusTerminalKind.INTERCITY;
            case TRAIN, FERRY, CAR -> null;
        };
    }

    /** 허브 하나를 거치는 길. */
    private record Transfer(TransferHub hub, int totalMinutes) {}

    /**
     * 출발 터미널을 <b>실제로 노선이 있는 코드</b>로 고른다(#507).
     *
     * <p>TAGO 목록에는 같은 이름·같은 자리인데 코드가 여러 개인 터미널이 있고(동대구 7개·전주 5개·
     * 동서울 4개·센트럴시티 2개), <b>그중 한쪽으로만 구간이 조회된다.</b> 좌표만 보면 DB 순서가 고르는데
     * 그건 우연이다 — 실제로 {@code NAEK020}(센트럴시티)→광주는 0편, {@code NAEK021}(같은 센트럴시티)→
     * 광주는 68편인데 우리는 0편 쪽을 쓰고 있었다. 서울에서 나가는 버스가 통째로 "운행 없음" 으로 보였다.
     *
     * <p>고르는 순서는 <b>아는 것부터</b>다.
     *
     * <ol>
     *   <li>이 도착지로 <b>다니는 것이 확인된</b> 코드
     *   <li>아직 안 재본 코드 — 모르는 것은 없는 것이 아니다
     *   <li>그래도 없으면 최근접(지금까지의 동작)
     * </ol>
     *
     * <p><b>외부를 안 친다.</b> 판정 근거는 이미 잰 구간뿐이고, 여기는 요청 경로다. 아직 안 잰 구간은
     * {@code minutesFor} 가 자리를 만들어 배치가 채운다(#491 이 그 순번을 앞으로 당긴다).
     */
    private Optional<Terminal> boardableDeparture(
            TransitMode mode, double originLat, double originLng, Terminal arrival) {
        List<Terminal> nearby = busTerminalResolver.candidatesNear(
                originLat, originLng, arrival.kind(), MAX_DEPARTURE_CANDIDATES);
        return nearby.stream()
                .filter(candidate -> transitDurationService
                        .measuredMinutes(mode, candidate.code(), arrival.code())
                        .isPresent())
                .findFirst()
                .or(() -> nearby.stream()
                        .filter(candidate -> !transitDurationService
                                .knownUnroutable(mode, candidate.code(), arrival.code()))
                        .findFirst())
                .or(() -> nearby.stream().findFirst());
    }

    /** 도착 쪽 지점 코드 — 구간 조회의 반대편이다. */
    private static Optional<String> arrivalCode(
            TransitMode mode, Optional<Terminal> destTerminal, Optional<Port> destPort) {
        return switch (mode) {
            case EXPRESS_BUS, INTERCITY_BUS -> destTerminal.map(Terminal::code);
            case FERRY -> destPort.map(Port::code);
            case TRAIN, CAR -> Optional.empty();
        };
    }

    /**
     * 버스·여객선의 시간표(#414) — <b>여행일이 조회창 안일 때만</b> 채워진다.
     *
     * <p>창 밖이면 {@link TransitDepartureService} 가 외부를 안 치고 빈 목록을 준다. 연차 기준으로 다음 달
     * 코스를 짜는 서비스라 대부분이 창 밖이고, 그때 이 경로는 호출을 한 건도 쓰지 않는다.
     *
     * <p>창 안일 때 <b>코스 하나에 한 건</b>이다 — 대표 수단의 구간 하나만 묻는다. 열차는 여기 오지 않는다
     * ({@code TrainAccessService} 가 이미 받아 둔 하루치에서 고른다).
     */
    private List<Departure> departuresOf(
            TransitMode mode, Optional<RegionArrival> departure,
            Optional<Terminal> destTerminal, Optional<Port> destPort, LocalDate date, LocalTime notBefore) {
        LocalDate today = LocalDate.now(SERVICE_ZONE);
        List<Departure> all = departure
                .flatMap(from -> arrivalCode(mode, destTerminal, destPort)
                        .map(toCode -> transitDepartureService.departures(
                                mode, from.code(), toCode, date, today)))
                .orElseGet(List::of);
        return Departure.upcoming(all, notBefore);
    }
}
