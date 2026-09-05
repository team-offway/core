package com.offway.core.transport.domain;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * (출발역·도착역·날짜)에 대한 열차 조회 결과 — 세 상태를 <b>구분</b>한다. "그 날짜에 운행이 없음"과 "조회 자체가 실패"는 UX 가
 * 완전히 다르기 때문이다(닫힌 계층이라 {@code sealed} + 패턴 매칭).
 *
 * <ul>
 *   <li>{@link Available} — 그 날짜에 운행하는 편이 있다.
 *   <li>{@link NoServiceOnDate} — 조회는 정상인데 그 날짜에 운행 열차가 없다 → 사용자에게 "해당 날짜엔 열차가 없어요" 안내.
 *   <li>{@link Unavailable} — 조회 자체가 불가(키 없음·호출 실패) → 사용자에게 알리지 말고 조용히 다른 수단으로 폴백.
 * </ul>
 */
public sealed interface TrainAvailability {

    /**
     * 그 날짜에 운행하는 편 전부 — <b>하루치를 통째로 들고 있는다</b>(#138).
     *
     * <p><b>왜 가장 빠른 한 편이 아닌가.</b> 출발 시각(연차 08시 · 반차 12시 · 반반차 15시)에 따라 탈 수 있는 편이
     * 달라진다. 조회 때 한 편으로 좁히면 그 시각을 캐시 키에 넣어야 하고, 그러면 같은 역쌍·같은 날짜에 외부
     * 호출이 <b>단위 수만큼 늘어난다</b>. TAGO 는 일일 한도를 다른 API 와 공유하므로(CLAUDE.md §외부 API 한도)
     * 하루치를 한 번 받아 우리가 고르는 쪽이 맞다.
     *
     * <p>TAGO 의 {@code depPlandTime} 에 시각까지 넘겨 서버가 걸러주게 할 수도 있지만, 실제로 필터가 걸리는지
     * 실호출로 확인하지 않았다. 확인되면 호출 자체를 줄이는 최적화로 따로 열되, 그때도 캐시는 하루치로 둔다.
     */
    record Available(List<TrainLeg> legs) implements TrainAvailability {

        public Available {
            if (legs == null || legs.isEmpty()) {
                // 편이 없으면 그것은 Available 이 아니라 NoServiceOnDate 다. 빈 목록을 허용하면
                // 호출부가 Optional 을 또 풀어야 하고, "있다는데 없다" 는 상태가 타입에 남는다.
                throw new IllegalArgumentException("열차편은 최소 한 편이어야 합니다");
            }
            legs = List.copyOf(legs);
        }

        /**
         * 이 시각 이후에 떠나는 편 중 <b>가장 일찍 닿는</b> 것.
         *
         * <p><b>비어 있을 수 있다.</b> 반반차로 15시에 나서는데 그 지역 막차가 14시면 그날 열차로는 못 간다 —
         * 그것을 "가장 빠른 편"(새벽 첫차)으로 답하면 지킬 수 없는 코스가 된다.
         *
         * <p><b>기준은 도착 시각이다.</b> 예전에는 소요시간으로 골랐고 "늦게 떠나도 빨리 닿는 편이 첫날을 더
         * 남긴다" 고 적어 뒀는데, 그 문장이 성립하지 않는다 — 늦게 떠나면 아무리 빨라도 늦게 닿는다. 실제로
         * 밤 8시에 떠나는 111분 편이 아침 8시 57분에 떠나는 121분 편을 이겨서, 코스가 그 늦은 도착을 첫날
         * 시작점으로 삼았다. 89곳 실측에서 첫날이 통째로 빈 지역이 아홉 곳이었다(#442).
         *
         * <p>이 값이 정하는 것은 <b>첫날에 무엇을 넣을 수 있는가</b>다. 그러니 재야 할 것은 "얼마나 빨리
         * 가는가" 가 아니라 "언제 도착하는가" 다.
         *
         * <p>도착이 같으면 <b>늦게 떠나는 편</b>을 고른다. 같은 시각에 닿는다면 역에서 기다리는 시간이 짧은
         * 쪽이 낫다.
         */
        public Optional<TrainLeg> earliestArrivalDepartingFrom(LocalTime notBefore) {
            return legs.stream()
                    .filter(leg -> !leg.departAt().toLocalTime().isBefore(notBefore))
                    .min(Comparator.comparing(TrainLeg::arriveAt)
                            .thenComparing(Comparator.comparing(TrainLeg::departAt).reversed()));
        }
    }

    /** 조회 정상, 그 날짜 운행 없음. */
    record NoServiceOnDate() implements TrainAvailability {}

    /** 조회 불가(키 없음·호출 실패) — 조용히 폴백. */
    record Unavailable() implements TrainAvailability {}
}
