package com.offway.core.transport.infrastructure;

import com.offway.core.common.geo.Coordinate;
import com.offway.core.transport.domain.BusTerminalKind;
import com.offway.core.transport.domain.Port;
import com.offway.core.transport.domain.Station;
import com.offway.core.transport.domain.Terminal;
import com.offway.core.transport.domain.TransitMode;
import com.offway.core.transport.domain.TransportMode;
import com.offway.core.transport.service.BusTerminalResolver;
import com.offway.core.transport.service.FerryPortResolver;
import com.offway.core.transport.service.TrainStationResolver;
import com.offway.core.transport.service.TransitDurationService;
import com.offway.core.transport.service.TravelTimeProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 대중교통 도달시간 — <b>역·터미널·항구를 거쳐</b> 계산한다(#58).
 *
 * <h2>무엇이 틀렸었나</h2>
 *
 * <p>{@link HaversineTravelTimeProvider} 는 출발지에서 <b>지역 중심까지 직선</b>을 긋고 평균속도로 나눈다.
 * 거점을 아예 보지 않으므로 <b>울릉군도 육로로 친다</b> — 배를 타야 한다는 사실이 계산에 없다.
 *
 * <p>그 값이 추천의 도달 필터를 움직인다. 못 가는 곳이 후보에 오르거나, 열차로 금방 닿는 곳이 빠진다.
 *
 * <h2>어떻게 바꾸나</h2>
 *
 * <pre>
 *   출발지 ──접근──▶ 출발 거점 ──간선──▶ 도착 거점 ──이탈──▶ 지역
 * </pre>
 *
 * <p><b>간선은 실측을 먼저 쓴다.</b> 이미 재 둔 구간 소요시간이 있으면 그 값이고, 없으면 수단별 평균속도로
 * 추정한다({@link TransitMode#trunkMinutes}). 접근·이탈은 짧은 구간이라 기존 대중교통 평균속도를 그대로 쓴다.
 *
 * <p><b>수단은 가장 빠른 것을 고른다.</b> 열차·고속버스·시외버스·여객선 각각으로 계산해 최솟값을 답한다 —
 * 사용자가 실제로 그렇게 고르기 때문이다.
 *
 * <p><b>여객선만 실측이 있을 때로 제한한다.</b> 항로는 있거나 없거나라서, 없는 항로를 평균속도로
 * 추정하면 갈 수 없는 곳이 "배로 가면 되는 곳" 이 된다. 버스·열차는 망이 촘촘해 그 위험이 작다.
 *
 * <h2>외부를 부르지 않는다</h2>
 *
 * <p>거점 해석은 시드를 인메모리로 들고 하는 좌표 최근접이고, 실측 조회는 DB 읽기다. <b>추천이 89곳을
 * 훑는 자리</b>라 여기서 외부 호출이 하나라도 나가면 화면 한 번에 89건이 된다.
 *
 * <p>같은 이유로 실측 조회도 <b>읽기 전용</b>이다({@code measuredMinutes}). 자리를 만드는 쪽을 쓰면 추천
 * 한 번이 최대 89건의 쓰기를 일으킨다.
 *
 * <h2>거점을 못 찾으면 직선으로 되돌아간다</h2>
 *
 * <p>출발지나 목적지 근처에 아무 거점도 없으면 예전 방식 그대로다. 오지에서는 정상이고, 그때도 코스는
 * 나가야 한다(로컬 실행성).
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class TransitReachTimeProvider implements TravelTimeProvider {

    /**
     * 접근·이탈 구간의 평균속도 — <b>기존 대중교통 값을 그대로 쓴다</b>.
     *
     * <p>집에서 역까지, 역에서 목적지까지는 시내 이동이라 간선과 성격이 다르다. 다만 짧은 구간이고
     * 전체에서 차지하는 비중이 작아, 새 숫자를 근거 없이 만들기보다 이미 쓰던 값을 유지한다.
     */
    private static final TransportMode ACCESS_MODE = TransportMode.TRANSIT;

    /**
     * 거점을 거치는 것이 <b>오히려 손해</b>인 거리.
     *
     * <p>가까운 지역은 역까지 갔다가 다시 나오는 것보다 직접 가는 편이 빠르다. 그 경우 계산이 실제보다
     * 크게 나오므로, 짧은 거리에서는 예전 방식을 그대로 쓴다.
     *
     * <p>{@value}㎞ 는 우리 89곳 중 서울에서 가장 가까운 축(연천·양평 등)이 그 언저리라 잡은 값이다.
     * 이보다 가까우면 애초에 대중교통 도달이 문제되지 않는다.
     */
    private static final double MIN_TRUNK_KM = 40;

    private final TrainStationResolver stationResolver;
    private final BusTerminalResolver busTerminalResolver;
    private final FerryPortResolver ferryPortResolver;
    private final TransitDurationService transitDurationService;
    private final HaversineTravelTimeProvider fallback;

    @Override
    public int reachMinutes(Coordinate origin, Coordinate destination, TransportMode mode) {
        Objects.requireNonNull(origin, "origin 는 null 일 수 없습니다.");
        Objects.requireNonNull(destination, "destination 는 null 일 수 없습니다.");
        Objects.requireNonNull(mode, "mode 는 null 일 수 없습니다.");

        if (mode != TransportMode.TRANSIT) {
            // 자차는 거점을 거치지 않는다. 실측이 필요하면 TmapRouteTimeProvider 가 따로 답한다.
            return fallback.reachMinutes(origin, destination, mode);
        }
        if (origin.haversineKmTo(destination) < MIN_TRUNK_KM) {
            return fallback.reachMinutes(origin, destination, mode);
        }

        List<Integer> byMode = new ArrayList<>();
        trainMinutes(origin, destination).ifPresent(byMode::add);
        busMinutes(origin, destination).ifPresent(byMode::add);
        ferryMinutes(origin, destination).ifPresent(byMode::add);

        if (byMode.isEmpty()) {
            // 양쪽 어디에도 거점이 없다 — 오지에서는 정상이다.
            return fallback.reachMinutes(origin, destination, mode);
        }
        return byMode.stream().min(Integer::compareTo).orElseThrow();
    }

    /** 열차 — 구간 소요시간 표를 쓰지 않는 수단이라 늘 추정이다(그쪽은 실제 시각을 직접 답한다). */
    private Optional<Integer> trainMinutes(Coordinate origin, Coordinate destination) {
        Optional<Station> from = stationResolver.nearest(origin.lat(), origin.lng());
        Optional<Station> to = stationResolver.nearest(destination.lat(), destination.lng());
        if (from.isEmpty() || to.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(viaPoints(
                origin, destination, from.get().coordinate(), to.get().coordinate(),
                TransitMode.TRAIN, null, null));
    }

    /**
     * 버스 — 같은 종류의 터미널끼리 이어야 한다.
     *
     * <p>고속버스 터미널에서 타서 시외버스 터미널에 내리는 노선은 없다. 종류를 섞으면 있지도 않은 구간의
     * 소요시간을 지어내는 셈이다.
     */
    private Optional<Integer> busMinutes(Coordinate origin, Coordinate destination) {
        List<Integer> candidates = new ArrayList<>();
        for (BusTerminalKind kind : BusTerminalKind.values()) {
            Optional<Terminal> from = busTerminalResolver.nearest(origin.lat(), origin.lng(), kind);
            Optional<Terminal> to = busTerminalResolver.nearest(destination.lat(), destination.lng(), kind);
            if (from.isEmpty() || to.isEmpty()) {
                continue;
            }
            candidates.add(viaPoints(
                    origin, destination, from.get().coordinate(), to.get().coordinate(),
                    TransitMode.of(kind), from.get().code(), to.get().code()));
        }
        return candidates.stream().min(Integer::compareTo);
    }

    /**
     * 여객선 — <b>실측이 있을 때만 쓴다. 추정하지 않는다.</b>
     *
     * <p>다른 수단과 다르게 다루는 이유는 <b>항로가 있거나 없거나</b>이기 때문이다. 버스·열차는 망이
     * 촘촘해 임의의 두 거점이 대체로 이어지고, 직결이 없으면 환승해서 간다. 배는 그렇지 않다 —
     * 인천에서 울릉으로 가는 배는 없고, 없는 항로를 평균속도로 추정하면 <b>"배로 가면 되겠네" 라는
     * 틀린 답</b>이 나온다. 그 값이 도달 필터를 통과하면 사용자는 갈 수 없는 곳을 후보로 본다.
     *
     * <p>실측({@code transit_leg_duration})이 있다는 것은 그 구간을 실제로 재 봤다는 뜻이라 항로가
     * 있다는 근거가 된다. 없으면 이 수단을 후보에서 뺀다 — 그러면 다른 수단이나 직선 추정으로 간다.
     */
    private Optional<Integer> ferryMinutes(Coordinate origin, Coordinate destination) {
        Optional<Port> from = ferryPortResolver.nearest(origin.lat(), origin.lng());
        Optional<Port> to = ferryPortResolver.nearest(destination.lat(), destination.lng());
        if (from.isEmpty() || to.isEmpty() || from.get().code().equals(to.get().code())) {
            return Optional.empty();
        }
        return transitDurationService
                .measuredMinutes(TransitMode.FERRY, from.get().code(), to.get().code())
                .map(trunk -> fallback.reachMinutes(origin, from.get().coordinate(), ACCESS_MODE)
                        + trunk
                        + fallback.reachMinutes(to.get().coordinate(), destination, ACCESS_MODE));
    }

    /**
     * 접근 + 간선 + 이탈.
     *
     * <p>간선은 <b>실측이 있으면 그것</b>이고, 없으면 수단별 평균속도 추정이다. 코드가 null 이면(열차)
     * 실측 표를 아예 묻지 않는다.
     */
    private int viaPoints(
            Coordinate origin, Coordinate destination,
            Coordinate boarding, Coordinate arrival,
            TransitMode mode, String depCode, String arrCode) {
        int access = fallback.reachMinutes(origin, boarding, ACCESS_MODE);
        int egress = fallback.reachMinutes(arrival, destination, ACCESS_MODE);
        int trunk = measuredTrunk(mode, depCode, arrCode)
                .orElseGet(() -> mode.trunkMinutes(boarding.haversineKmTo(arrival)));
        return access + trunk + egress;
    }

    private Optional<Integer> measuredTrunk(TransitMode mode, String depCode, String arrCode) {
        if (depCode == null || arrCode == null) {
            return Optional.empty();
        }
        return transitDurationService.measuredMinutes(mode, depCode, arrCode);
    }
}
