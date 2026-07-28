package com.offway.core.transport.domain;

/**
 * (출발역·도착역·날짜)에 대한 열차 조회 결과 — 세 상태를 <b>구분</b>한다. "그 날짜에 운행이 없음"과 "조회 자체가 실패"는 UX 가
 * 완전히 다르기 때문이다(닫힌 계층이라 {@code sealed} + 패턴 매칭).
 *
 * <ul>
 *   <li>{@link Available} — 운행하는 가장 빠른 열차가 있다.
 *   <li>{@link NoServiceOnDate} — 조회는 정상인데 그 날짜에 운행 열차가 없다 → 사용자에게 "해당 날짜엔 열차가 없어요" 안내.
 *   <li>{@link Unavailable} — 조회 자체가 불가(키 없음·호출 실패) → 사용자에게 알리지 말고 조용히 다른 수단으로 폴백.
 * </ul>
 */
public sealed interface TrainAvailability {

    /** 운행하는 가장 빠른 열차. */
    record Available(TrainLeg fastest) implements TrainAvailability {
        public Available {
            if (fastest == null) {
                throw new IllegalArgumentException("열차편은 필수입니다");
            }
        }
    }

    /** 조회 정상, 그 날짜 운행 없음. */
    record NoServiceOnDate() implements TrainAvailability {}

    /** 조회 불가(키 없음·호출 실패) — 조용히 폴백. */
    record Unavailable() implements TrainAvailability {}
}
