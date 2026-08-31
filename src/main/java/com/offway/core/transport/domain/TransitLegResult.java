package com.offway.core.transport.domain;

/**
 * 버스·여객선 구간 조회 결과(#107 · #97) — 세 상태를 <b>구분</b>한다. {@link TrainAvailability} 와 같은 판단이다.
 *
 * <p><b>왜 굳이 가르나.</b> 우리는 결과를 DB 에 영구 기록한다. "그 구간은 안 다닌다" 로 적으면 배치가 다시는
 * 재지 않는데, 그 판단이 <b>키가 없어서</b>·<b>한도가 말라서</b> 나온 것이면 멀쩡한 구간이 영원히 미운행으로
 * 굳는다. 화면에는 아무 흔적도 남지 않는다 — 소요시간이 빠진 코스가 그냥 정상처럼 보인다.
 *
 * <ul>
 *   <li>{@link Measured} — 쟀다. 그대로 기록한다.
 *   <li>{@link NoService} — 조회는 정상인데 그 구간에 편이 없다. <b>이것도 결과</b>라 기록해야 다시 안 잰다.
 *   <li>{@link Unavailable} — 조회 자체가 불가(키 없음·호출 실패·비정상 resultCode). <b>기록하지 않고</b>
 *       다음 배치에서 다시 시도한다.
 * </ul>
 */
public sealed interface TransitLegResult {

    record Measured(MeasuredLeg leg) implements TransitLegResult {
        public Measured {
            if (leg == null) {
                throw new IllegalArgumentException("잰 구간은 null 일 수 없습니다 — 값이 없으면 NoService 입니다.");
            }
        }
    }

    /** 조회 정상, 그 구간 운행 없음. */
    record NoService() implements TransitLegResult {}

    /** 조회 불가 — 기록하지 않고 다시 시도한다. */
    record Unavailable() implements TransitLegResult {}
}
