package com.offway.core.transport.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 화면에 올릴 시간표를 고르는 규칙(#414).
 *
 * <p>여기서 잠그는 것은 셋이다 — <b>탈 수 없는 편을 보여주지 않는 것</b>, <b>이른 순으로 세우는 것</b>,
 * 그리고 <b>카드가 화면을 넘지 않는 것</b>.
 */
class DepartureTest {

    private static final LocalDateTime 기준일 = LocalDateTime.of(2026, 9, 5, 0, 0);

    private static Departure 편(int hour, int minute, int durationMinutes) {
        LocalDateTime depart = 기준일.withHour(hour).withMinute(minute);
        return new Departure("무궁화호", depart, depart.plusMinutes(durationMinutes));
    }

    @Test
    void 소요시간은_출발과_도착에서_나온다() {
        assertEquals(149, 편(7, 20, 149).durationMinutes());
    }

    @Test
    void 도착이_출발보다_빠르면_만들_수_없다() {
        LocalDateTime depart = 기준일.withHour(9);
        assertThrows(IllegalArgumentException.class, () -> new Departure("KTX", depart, depart.minusMinutes(1)));
    }

    /** 0분 이동은 없다 — 같은 시각이면 파싱이 깨졌거나 응답이 이상한 것이다. */
    @Test
    void 출발과_도착이_같으면_만들_수_없다() {
        LocalDateTime same = 기준일.withHour(9);
        assertThrows(IllegalArgumentException.class, () -> new Departure("KTX", same, same));
    }

    /**
     * <b>못 타는 편은 안 보여준다.</b>
     *
     * <p>반반차로 15시에 나서는데 07시 차를 시간표에 올리면, 사용자는 있지도 않은 선택지를 본다.
     */
    @Test
    void 집을_나서기_전에_떠나는_편은_뺀다() {
        List<Departure> 편들 = List.of(편(7, 20, 149), 편(14, 0, 140), 편(16, 30, 130));

        List<Departure> 남은것 = Departure.upcoming(편들, LocalTime.of(15, 0));

        assertEquals(1, 남은것.size());
        assertEquals(16, 남은것.getFirst().departAt().getHour());
    }

    /**
     * 정렬은 <b>출발 순</b>이다 — 소요시간 순이 아니다.
     *
     * <p>가장 빨리 닿는 편은 카드 위쪽이 이미 말하고 있다. 시간표가 답하는 질문은 "다음 차가 몇 시인가" 다.
     */
    @Test
    void 이른_순으로_세운다() {
        // 09시 차가 가장 빠르지만(100분) 시간표에서는 07시 차가 먼저다.
        List<Departure> 편들 = List.of(편(9, 0, 100), 편(7, 20, 149), 편(8, 10, 200));

        List<Departure> 정렬됨 = Departure.upcoming(편들, LocalTime.MIDNIGHT);

        assertEquals(List.of(7, 8, 9), 정렬됨.stream().map(d -> d.departAt().getHour()).toList());
    }

    /** TAGO 는 한 구간에 최대 50편을 준다. 그대로 내리면 교통 카드 하나가 화면을 넘긴다. */
    @Test
    void 상한을_넘겨_내리지_않는다() {
        List<Departure> 많음 = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> 편(6 + i % 18, 0, 100))
                .toList();

        assertEquals(Departure.MAX_SHOWN, Departure.upcoming(많음, LocalTime.MIDNIGHT).size());
    }

    /**
     * 막차가 지나면 <b>빈 목록</b>이다 — 예외가 아니다.
     *
     * <p>그날 그 시각 이후로 갈 수 없다는 정상 결과이고, 화면은 시간표 줄만 접는다.
     */
    @Test
    void 탈_수_있는_편이_없으면_빈_목록이다() {
        assertTrue(Departure.upcoming(List.of(편(7, 20, 149)), LocalTime.of(23, 0)).isEmpty());
    }
}
