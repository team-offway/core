package com.offway.core.transport.service.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.transport.domain.Departure;
import com.offway.core.transport.domain.TransitMode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 대안 수단이 <b>시간표까지</b> 든다(#414).
 *
 * <p>"무엇으로, 어디에, 몇 분" 만으로는 사용자가 대안을 고를 수 없다 — 시외버스가 40분 더 걸려도 지금
 * 바로 타는 편이 있으면 그쪽을 고른다. 그 판단에 시각이 필요하다.
 */
class TransitOptionTest {

    private static Departure 편(int hour) {
        LocalDateTime depart = LocalDateTime.of(2026, 9, 5, hour, 0);
        return new Departure("우등", depart, depart.plusMinutes(150));
    }

    @Test
    void 대안도_시간표를_든다() {
        TransitOption 대안 = TransitOption.builder()
                .mode(TransitMode.FERRY)
                .toName("울릉_도동")
                .durationMinutes(140)
                .departures(List.of(편(9)))
                .build();

        assertEquals(1, 대안.departures().size());
        assertEquals(9, 대안.departures().getFirst().departAt().getHour());
    }

    /**
     * 시간표가 없어도 대안은 성립한다.
     *
     * <p>조회창 밖이면 어느 수단도 시간표를 못 얻는데, 그때도 "이 지역에 배로도 갈 수 있다" 는 사실은
     * 그대로다. null 이 아니라 <b>빈 목록</b>이라 화면이 유무를 분기하지 않는다.
     */
    @Test
    void 시간표를_안_주면_빈_목록이다() {
        TransitOption 대안 = TransitOption.builder()
                .mode(TransitMode.INTERCITY_BUS)
                .toName("정선")
                .build();

        assertTrue(대안.departures().isEmpty());
    }

    @Test
    void 도착_지점명_없이는_대안이_아니다() {
        // 어디에 내리는지 모르면 화면이 그릴 것이 없다 — "없는 선택지" 를 늘어놓는 셈이다.
        assertThrows(NullPointerException.class,
                () -> TransitOption.builder().mode(TransitMode.FERRY).build());
    }
}
