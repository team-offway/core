package com.offway.core.transport.service;

import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.domain.TransportMode;

/**
 * 도달시간 port — transport 도메인이 다른 도메인(trip 추천·itinerary 코스)에 노출하는 공개 계약. 출발지에서 목적지까지 해당
 * 이동수단의 도달시간(분)을 준다.
 *
 * <p>구현은 교체된다: 현재는 직선거리 기반 interim({@code HaversineTravelTimeProvider}), TMAP(#25) 이 붙으면
 * 실측 어댑터가 이 port 를 대신 구현한다. 소비 도메인은 이 인터페이스에만 의존한다.
 */
public interface TravelTimeProvider {

    /**
     * 출발지 → 목적지 도달시간(분).
     *
     * @param origin 출발 좌표
     * @param destination 목적 좌표
     * @param mode 이동수단
     */
    int reachMinutes(Coordinate origin, Coordinate destination, TransportMode mode);
}
