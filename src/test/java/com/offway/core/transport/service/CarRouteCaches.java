package com.offway.core.transport.service;

import com.offway.core.transport.domain.CarLegDuration;
import com.offway.core.transport.domain.CarRouteOrder;
import com.offway.core.transport.domain.CoordinateKey;
import com.offway.core.transport.repository.CarRouteCacheRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 단위 테스트용 자차 경로 캐시(#584) — 표 대신 맵에 담는다.
 *
 * <p>어댑터({@code TmapRouteTimeProvider}·{@code TmapRouteOptimizer}) 단위 테스트가 쓴다. 그 테스트가
 * 보는 것은 <b>TMAP 응답을 어떻게 번역하는가</b>이지 캐시가 아니라서, DB 를 띄우지 않고 이 자리만 채운다.
 *
 * <p>{@link #empty()} 는 <b>항상 비어 있는 캐시</b>다 — 캐시가 없던 시절과 같은 경로로 돌게 해, 기존
 * 단위 테스트의 의미가 바뀌지 않게 한다.
 */
public final class CarRouteCaches {

    private CarRouteCaches() {
    }

    /** 아무것도 기억하지 않는 캐시 — 매번 TMAP 으로 간다. */
    public static CarRouteCacheService empty() {
        return new CarRouteCacheService(new InMemory(false));
    }

    /** 기억하는 캐시 — 두 번째 호출이 적중하는지 보는 테스트가 쓴다. */
    public static CarRouteCacheService remembering() {
        return new CarRouteCacheService(new InMemory(true));
    }

    private static final class InMemory implements CarRouteCacheRepository {

        private final boolean remembers;
        private final Map<String, CarLegDuration> legs = new HashMap<>();
        private final Map<String, CarRouteOrder> orders = new HashMap<>();

        private InMemory(boolean remembers) {
            this.remembers = remembers;
        }

        @Override
        public Optional<CarLegDuration> findLeg(CoordinateKey from, CoordinateKey to) {
            return Optional.ofNullable(legs.get(legKey(from, to)));
        }

        @Override
        public void saveLeg(CarLegDuration leg) {
            if (!remembers) {
                return;
            }
            legs.put(leg.getFromLat() + "," + leg.getFromLng() + ";" + leg.getToLat() + "," + leg.getToLng(), leg);
        }

        @Override
        public Optional<CarRouteOrder> findOrder(String pointsKey) {
            return Optional.ofNullable(orders.get(pointsKey));
        }

        @Override
        public void saveOrder(CarRouteOrder order) {
            if (!remembers) {
                return;
            }
            orders.put(order.getPoints(), order);
        }

        private static String legKey(CoordinateKey from, CoordinateKey to) {
            return from.lat() + "," + from.lng() + ";" + to.lat() + "," + to.lng();
        }
    }
}
