package com.offway.core.common.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 알림에 실리는 주체 내역 한 줄(#285).
 *
 * <p>이 줄이 "어디서 새나" 에 답하는 자리다. 지금까지 알림은 초과 사실만 말해 받고도 할 일이 없었다.
 */
class CallerBreakdownTest {

    @Test
    void 많이_쓴_순으로_싣는다() {
        // 저장소가 정렬해 주더라도 여기서 다시 세운다 — 호출 경로가 늘 때 같은 규칙을 다시 지키지 않게.
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("장소상세", 91L);
        counts.put("코스생성", 320L);
        counts.put("중심관광지배치", 89L);

        assertEquals("코스생성 320 · 장소상세 91 · 중심관광지배치 89", CallerBreakdown.of(counts).describe());
    }

    /**
     * <b>알림 개수를 늘리지 않는 것이 이 작업의 전제다.</b> 줄 길이가 주체 수만큼 자라면 같은 문제를 다른
     * 방식으로 만드는 셈이라, 상위 넷만 싣고 나머지는 접는다.
     */
    @Test
    void 다섯_이상이면_나머지를_접는다() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("코스생성", 100L);
        counts.put("장소상세", 90L);
        counts.put("지역콘텐츠배치", 80L);
        counts.put("중심관광지배치", 70L);
        counts.put("홈", 5L);
        counts.put("추천", 3L);
        counts.put("미상", 2L);

        assertEquals("코스생성 100 · 장소상세 90 · 지역콘텐츠배치 80 · 중심관광지배치 70 · 그 외 3곳 10",
                CallerBreakdown.of(counts).describe());
    }

    @Test
    void 호출_수가_같으면_이름_순으로_갈라_결과가_흔들리지_않게_한다() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("나", 10L);
        counts.put("가", 10L);

        assertEquals("가 10 · 나 10", CallerBreakdown.of(counts).describe());
    }

    /** 실을 것이 없으면 빈 문자열이라, 호출부가 줄을 안 붙이고 그대로 넘어간다. */
    @Test
    void 내역이_없으면_비어_있다() {
        CallerBreakdown empty = CallerBreakdown.of(Map.of());

        assertTrue(empty.isEmpty());
        assertEquals("", empty.describe());
    }

    @Test
    void 넷_이하면_그_외를_붙이지_않는다() {
        CallerBreakdown breakdown = CallerBreakdown.of(Map.of("코스생성", 5L));

        assertFalse(breakdown.isEmpty());
        assertEquals("코스생성 5", breakdown.describe());
    }
}
