package com.offway.core.transport.infrastructure.tago;

import com.offway.core.transport.domain.BusArrivalStatus;
import com.offway.core.transport.domain.BusStop;

/**
 * TAGO 버스도착정보(base {@code ArvlInfoInqireService}) 조회 port. 한 정류소에 곧 도착할 버스를 준다.
 *
 * <p><b>실시간</b> 정보라 여행 <b>당일</b>에만 쓸 수 있다. 다음 달 코스를 짜는 계획 시점에는 의미가 없다 — 계획 시점의
 * 대중교통 판단은 {@link BusStopClient}(정류소 유무)가 맡는다.
 *
 * <p>결과는 세 상태를 구분한다({@link BusArrivalStatus}): 곧 도착 있음 / 당장 없음 / 조회 불가.
 */
public interface BusArrivalClient {

    /**
     * 정류소의 실시간 도착 예정 버스를 조회한다.
     *
     * @param stop 조회할 정류소. 도시코드와 정류소 코드가 함께 필요해 {@link BusStop} 를 통째로 받는다
     * @return {@link BusArrivalStatus} — 곧 도착/당장 없음/조회 불가
     */
    BusArrivalStatus arrivalsAt(BusStop stop);
}
