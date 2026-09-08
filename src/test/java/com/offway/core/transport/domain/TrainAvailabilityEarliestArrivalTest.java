package com.offway.core.transport.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 코스가 탈 편을 고르는 규칙 — <b>가장 일찍 닿는 편</b>(#442).
 *
 * <p><b>왜 도착 시각인가.</b> 이 값이 정하는 것은 첫날에 무엇을 넣을 수 있는가다. 예전에는 소요시간으로
 * 골랐는데, 그러면 밤에 떠나는 짧은 편이 아침에 떠나는 긴 편을 이긴다 — 늦게 떠나면 아무리 빨라도 늦게
 * 닿으므로 첫날이 통째로 사라진다.
 *
 * <p>89곳 실측에서 첫날에 관광·맛집이 하나도 없는 지역이 아홉 곳이었고, 전부 이 규칙 때문이었다.
 */
class TrainAvailabilityEarliestArrivalTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 6);

    private static TrainLeg leg(String type, int departHour, int departMinute, int arriveHour, int arriveMinute) {
        return TrainLeg.of(
                type,
                LocalDateTime.of(DAY, LocalTime.of(departHour, departMinute)),
                LocalDateTime.of(DAY, LocalTime.of(arriveHour, arriveMinute)));
    }

    /**
     * 실측한 양양군 사례 그대로다(#442·#443).
     *
     * <p>08:57 에 떠나 10:58 에 닿는 편(121분)과, 밤 8시에 떠나 22:11 에 닿는 편(111분)이 있었다.
     * 소요시간으로 고르면 뒤엣것이 이겨서 코스가 밤 10시 도착을 첫날 시작점으로 삼았다.
     */
    @Test
    void 늦게_떠나는_짧은_편보다_일찍_닿는_편을_고른다() {
        TrainAvailability.Available available = new TrainAvailability.Available(List.of(
                leg("KTX-이음", 20, 9, 22, 0), // 111분 — 예전 규칙의 승자
                leg("KTX-이음", 8, 57, 10, 58))); // 121분 — 실제로 타야 할 편

        TrainLeg chosen = available.earliestArrivalDepartingFrom(LocalTime.MIN).orElseThrow();

        assertEquals(LocalDateTime.of(DAY, LocalTime.of(10, 58)), chosen.arriveAt());
        assertTrue(chosen.durationMinutes() > 111, "소요시간이 더 길어도 일찍 닿는 편이 이긴다");
    }

    /** 집을 나서는 시각보다 먼저 떠나는 편은 아무리 일찍 닿아도 못 탄다(#138). */
    @Test
    void 나서는_시각_전에_떠나는_편은_고르지_않는다() {
        TrainAvailability.Available available = new TrainAvailability.Available(List.of(
                leg("무궁화호", 6, 0, 9, 30), // 가장 일찍 닿지만 반차로 12시에 나서면 못 탄다
                leg("ITX-마음", 12, 22, 14, 27)));

        TrainLeg chosen = available.earliestArrivalDepartingFrom(LocalTime.of(12, 0)).orElseThrow();

        assertEquals("ITX-마음", chosen.trainType());
    }

    /** 막차가 이미 지났으면 빈 값이다 — 새벽 첫차를 답하면 지킬 수 없는 코스가 된다. */
    @Test
    void 나서는_시각_이후_편이_없으면_비어_있다() {
        TrainAvailability.Available available =
                new TrainAvailability.Available(List.of(leg("무궁화호", 6, 0, 9, 30)));

        assertTrue(available.earliestArrivalDepartingFrom(LocalTime.of(15, 0)).isEmpty());
    }

    /**
     * 도착이 같으면 <b>늦게 떠나는 편</b>을 고른다.
     *
     * <p>같은 시각에 닿는다면 역에서 기다리는 시간이 짧은 쪽이 낫다. 규칙을 정해 두지 않으면 목록 순서에
     * 따라 답이 달라져, 같은 요청이 날마다 다른 편을 준다.
     */
    @Test
    void 도착이_같으면_늦게_떠나는_편을_고른다() {
        TrainAvailability.Available available = new TrainAvailability.Available(List.of(
                leg("무궁화호", 7, 0, 11, 0),
                leg("KTX", 9, 30, 11, 0)));

        assertEquals("KTX", available.earliestArrivalDepartingFrom(LocalTime.MIN).orElseThrow().trainType());
    }
}
