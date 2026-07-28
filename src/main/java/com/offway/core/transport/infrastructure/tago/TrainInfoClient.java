package com.offway.core.transport.infrastructure.tago;

import com.offway.core.transport.infrastructure.tago.dto.TrainLeg;
import java.time.LocalDate;
import java.util.Optional;

/**
 * TAGO 열차정보(국토교통부, {@code TrainInfoInqireService}) 조회 port. 출발역→도착역·날짜로 그 날 운행 열차 중 가장 빠른
 * 편을 준다(KTX 포함).
 *
 * <p>키가 없거나 호출 실패·해당 날짜 미운행이면 <b>빈 Optional</b>. 대중교통 이동시간은 코스의 보조 정보라 502 로 올리기보다
 * 직선거리 근사로 폴백하는 게 낫다(graceful degradation) — TMAP 자차 클라이언트와 같은 정책.
 */
public interface TrainInfoClient {

    /**
     * 출발역→도착역, 해당 날짜의 가장 빠른(소요시간 최소) 열차.
     *
     * @param depStationId 출발역 코드(TAGO 역 ID, 예: {@code NAT010000})
     * @param arrStationId 도착역 코드
     * @param date 운행일자
     * @return 가장 빠른 열차편. 키 없음·실패·미운행 시 빈 Optional
     */
    Optional<TrainLeg> fastestTrain(String depStationId, String arrStationId, LocalDate date);
}
