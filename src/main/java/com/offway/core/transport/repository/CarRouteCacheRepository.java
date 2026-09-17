package com.offway.core.transport.repository;

import com.offway.core.transport.domain.CarLegDuration;
import com.offway.core.transport.domain.CarRouteOrder;
import com.offway.core.transport.domain.CoordinateKey;
import java.util.Optional;

/**
 * 자차 경로 캐시 port(#584) — 구간 소요시간과 경유지 최적 순서.
 *
 * <p>둘을 한 port 에 둔다. 표는 둘이지만 <b>같은 이유로 존재하고 같은 자리에서 쓰인다</b> —
 * TMAP 을 다시 안 부르려고 TMAP 어댑터 둘이 각각 본다. 나눠 두면 같은 규칙(폴백은 저장 안 한다,
 * 실패로 코스를 막지 않는다)을 두 곳에 적게 된다.
 */
public interface CarRouteCacheRepository {

    /** 이 구간의 아직 쓸 수 있는 실측값. */
    Optional<CarLegDuration> findLeg(CoordinateKey from, CoordinateKey to);

    /** 잰 값을 남긴다 — 같은 구간이 이미 있으면 덮어쓴다(다시 잰 것이다). */
    void saveLeg(CarLegDuration leg);

    /** 이 좌표 목록의 아직 쓸 수 있는 최적 순서. */
    Optional<CarRouteOrder> findOrder(String pointsKey);

    /** 잰 순서를 남긴다 — 같은 목록이 이미 있으면 덮어쓴다. */
    void saveOrder(CarRouteOrder order);
}
