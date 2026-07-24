package com.offway.core.transport.service;

import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.domain.TransportMode;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 직선거리 기반 도달시간 interim 구현. 대권거리에 우회계수를 곱해 도로거리를 근사하고, 이동수단의 평균속도로 시간을 낸다.
 *
 * <p>TMAP 실측(#25) 전까지 쓰는 근사다. TMAP 어댑터가 붙으면 이 빈을 {@code @Primary} 로 대체하고 이 구현은 폴백으로
 * 남긴다(키·쿼터 소진 시). 정확 캘리브레이션은 그때 한다.
 */
@Component
public class HaversineTravelTimeProvider implements TravelTimeProvider {

    /** 직선거리 → 도로거리 보정. 실도로는 대권거리보다 길다(산악·우회). interim 잠정치. */
    private static final double DETOUR_FACTOR = 1.25;

    @Override
    public int reachMinutes(Coordinate origin, Coordinate destination, TransportMode mode) {
        Objects.requireNonNull(origin, "origin 는 null 일 수 없습니다.");
        Objects.requireNonNull(destination, "destination 는 null 일 수 없습니다.");
        Objects.requireNonNull(mode, "mode 는 null 일 수 없습니다.");
        double roadKm = origin.haversineKmTo(destination) * DETOUR_FACTOR;
        return mode.travelMinutes(roadKm);
    }
}
