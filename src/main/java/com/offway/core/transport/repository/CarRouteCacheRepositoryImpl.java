package com.offway.core.transport.repository;

import com.offway.core.transport.domain.CarLegDuration;
import com.offway.core.transport.domain.CarRouteOrder;
import com.offway.core.transport.domain.CoordinateKey;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * port 구현(adapter) — Spring Data 에 위임.
 *
 * <p><b>쓰기는 어댑터가 트랜잭션을 연다.</b> 이 캐시를 채우는 자리는 TMAP 어댑터 안이고, 거기는
 * <b>트랜잭션 밖</b>이다(외부 호출은 트랜잭션에 넣지 않는다 — persistence-convention). 호출자가
 * 트랜잭션을 들고 있으리라 기대할 수 없다.
 *
 * <p>같은 키가 이미 있으면 <b>덮어쓴다</b>. 다시 잰 것이라 새 값이 맞다.
 */
@Repository
@RequiredArgsConstructor
public class CarRouteCacheRepositoryImpl implements CarRouteCacheRepository {

    private final CarLegDurationJpaRepository carLegDurationJpaRepository;
    private final CarRouteOrderJpaRepository carRouteOrderJpaRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<CarLegDuration> findLeg(CoordinateKey from, CoordinateKey to) {
        return carLegDurationJpaRepository.findByFromLatAndFromLngAndToLatAndToLng(
                from.lat(), from.lng(), to.lat(), to.lng());
    }

    @Override
    @Transactional
    public void saveLeg(CarLegDuration leg) {
        carLegDurationJpaRepository.upsert(
                leg.getFromLat(), leg.getFromLng(), leg.getToLat(), leg.getToLng(),
                leg.getMinutes(), leg.getMeasuredAt());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CarRouteOrder> findOrder(String pointsKey) {
        return carRouteOrderJpaRepository.findByPoints(pointsKey);
    }

    @Override
    @Transactional
    public void saveOrder(CarRouteOrder order) {
        carRouteOrderJpaRepository.upsert(
                order.getPoints(), order.getOrdinals(), order.getMeasuredAt());
    }
}
