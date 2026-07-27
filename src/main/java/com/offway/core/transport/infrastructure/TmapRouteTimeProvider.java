package com.offway.core.transport.infrastructure;

import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.domain.TransportMode;
import com.offway.core.transport.infrastructure.tmap.TmapClient;
import com.offway.core.transport.infrastructure.tmap.dto.TmapRoute;
import com.offway.core.transport.service.RouteTimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 자동차 실측 이동시간 어댑터 — TMAP 실측을 우선 쓰고, 키 없음·쿼터 소진·호출 실패면 직선거리 근사
 * ({@link HaversineTravelTimeProvider})로 폴백한다. 폴백은 코스가 끊기지 않게 하는 안전망이다.
 */
@Component
@RequiredArgsConstructor
public class TmapRouteTimeProvider implements RouteTimeProvider {

    private final TmapClient tmapClient;
    private final HaversineTravelTimeProvider fallback;

    @Override
    public int drivingMinutes(Coordinate from, Coordinate to) {
        return tmapClient.carRoute(from, to)
                .map(TmapRoute::durationMinutes)
                .orElseGet(() -> fallback.reachMinutes(from, to, TransportMode.CAR));
    }
}
