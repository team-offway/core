package com.offway.core.transport.domain;

import java.util.Comparator;
import java.util.List;

/**
 * 한 정류소의 실시간 버스 도착 조회 결과 — 세 상태를 <b>구분</b>한다(닫힌 계층이라 {@code sealed} + 패턴 매칭).
 *
 * <ul>
 *   <li>{@link Arriving} — 곧 도착할 버스가 있다(빠른 순).
 *   <li>{@link NoBusSoon} — 조회 정상, 당장 오는 버스 없음 → "지금은 오는 버스가 없어요" 안내 가능.
 *   <li>{@link Unavailable} — 조회 불가(키 없음·호출 실패) → 조용히 생략.
 * </ul>
 */
public sealed interface BusArrivalStatus {

    /** 곧 도착할 버스들(빠른 순). 비어 있을 수 없다 — 비면 {@link NoBusSoon} 이다. */
    record Arriving(List<BusArrival> arrivals) implements BusArrivalStatus {

        public Arriving {
            if (arrivals == null || arrivals.isEmpty()) {
                throw new IllegalArgumentException("도착 예정 버스가 하나 이상이어야 합니다. 비었으면 NoBusSoon 입니다");
            }
            arrivals = arrivals.stream()
                    .sorted(Comparator.comparingInt(BusArrival::arrivalSeconds))
                    .toList();
        }

        /** 가장 먼저 오는 버스. */
        public BusArrival soonest() {
            return arrivals.getFirst();
        }
    }

    /** 조회 정상, 당장 오는 버스 없음. */
    record NoBusSoon() implements BusArrivalStatus {}

    /** 조회 불가(키 없음·호출 실패) — 조용히 폴백. */
    record Unavailable() implements BusArrivalStatus {}
}
