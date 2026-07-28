package com.offway.core.transport.infrastructure.tago;

import com.offway.core.transport.domain.TrainAvailability;
import java.time.LocalDate;

/**
 * TAGO 열차정보(국토교통부, base {@code TrainInfo}) 조회 port. 출발역→도착역·날짜로 그 날 운행 열차 중 가장 빠른 편을 준다
 * (KTX 포함). 역 코드는 기차역 마스터(시드)에서 얻는다.
 *
 * <p>결과는 세 상태를 구분한다({@link TrainAvailability}): 운행 있음 / 그 날짜 운행 없음 / 조회 불가. "없음"과 "실패"는
 * UX 가 달라 뭉뚱그리지 않는다.
 */
public interface TrainInfoClient {

    /**
     * 출발역→도착역, 해당 날짜의 가장 빠른(소요시간 최소) 열차 조회.
     *
     * @param depStationId 출발역 코드(TAGO 역 ID, 예: {@code NAT010000})
     * @param arrStationId 도착역 코드
     * @param date 운행일자
     * @return {@link TrainAvailability} — 운행 있음/그 날짜 없음/조회 불가
     */
    TrainAvailability fastestTrain(String depStationId, String arrStationId, LocalDate date);
}
