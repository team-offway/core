package com.offway.core.transport.infrastructure;

import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.domain.TransportMode;
import com.offway.core.transport.infrastructure.tmap.TmapClient;
import com.offway.core.transport.infrastructure.tmap.dto.CarRouteResult;
import com.offway.core.transport.service.RouteTimeProvider;
import com.offway.core.transport.service.UnroutableCoordinateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 자동차 실측 이동시간 어댑터 — TMAP 실측을 우선 쓰고, 키 없음·쿼터 소진·호출 실패면 직선거리 근사
 * ({@link HaversineTravelTimeProvider})로 폴백한다. 폴백은 코스가 끊기지 않게 하는 안전망이다.
 *
 * <p><b>폴백했다는 사실을 삼키지 않는다(#335).</b> 예전에는 어떤 실패든 조용히 직선거리로 떨어졌다.
 * 그래서 도로에 안 붙는 좌표(귀목봉 · 해발 1,036m 산 정상)가 낀 코스는 산을 직선으로 넘는 시간을 화면에
 * 띄우면서 200 으로 정상 응답했고, 사용자는 그게 틀린 값인지 알 방법이 없었다.
 *
 * <p>이제 <b>좌표 탓인 거절만</b> 골라 기억한다. 타임아웃·한도 소진은 다음에 성공할 수 있으므로 기록하지
 * 않는다 — 그걸로 장소를 빼면 멀쩡한 곳이 영구히 사라진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TmapRouteTimeProvider implements RouteTimeProvider {

    private final TmapClient tmapClient;
    private final HaversineTravelTimeProvider fallback;
    private final UnroutableCoordinateService unroutableCoordinateService;

    @Override
    public int drivingMinutes(Coordinate from, Coordinate to) {
        return switch (tmapClient.carRoute(from, to)) {
            case CarRouteResult.Found found -> found.route().durationMinutes();
            case CarRouteResult.Rejected rejected -> {
                // 좌표를 기록해 다음 코스에서 뺀다. 이 코스는 이미 조립된 뒤라 여기서 되돌리지 않는다 —
                // 후보를 다시 골라 처음부터 짜면 외부 호출이 배로 는다.
                unroutableCoordinateService.report(from, to, rejected.reason());
                log.warn("경로 불가 구간 — 직선거리로 폴백하고 좌표를 기록한다 reason={}", rejected.reason());
                yield straightLine(from, to);
            }
            case CarRouteResult.Unavailable ignored -> straightLine(from, to);
        };
    }

    private int straightLine(Coordinate from, Coordinate to) {
        return fallback.reachMinutes(from, to, TransportMode.CAR);
    }
}
