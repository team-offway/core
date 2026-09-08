package com.offway.core.itinerary.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 하루에 같은 종류가 몰리지 않게 세는 자(#522).
 *
 * <p>실측이 두 층을 정했다 — 좁은 칸만 보면 태안 1일차의 <b>바다 4곳</b>(해수욕장·항구·해안이 서로 다른
 * 코드)을 못 잡고, 넓은 칸만 보면 산·계곡·해변이 뭉쳐 과하게 걸린다.
 */
class SightVarietyTest {

    private static final String BEACH = "A01011200";
    private static final String PORT = "A01011400";
    private static final String COAST = "A01010500";
    private static final String RELIC = "A02010700";

    /** "해수욕장 두 곳" 이 안 나오는 것이 이 기능의 최소선이다. */
    @Test
    void 같은_종류는_하루에_한_곳뿐이다() {
        SightVariety day = SightVariety.strict();

        assertTrue(day.accepts(BEACH));
        day.add(BEACH);

        assertFalse(day.accepts(BEACH), "해수욕장 두 곳은 절대 안 나와야 한다");
    }

    /** 좁은 칸이 다르면 통과하지만, 넓은 칸에서 결국 걸린다 — 태안 1일차의 바다 4곳이 그 모양이다. */
    @Test
    void 종류가_달라도_같은_계열이_넷이면_걸린다() {
        SightVariety day = SightVariety.strict();

        day.add(BEACH);
        assertTrue(day.accepts(PORT), "해수욕장 다음 항구는 다른 종류다");
        day.add(PORT);
        assertTrue(day.accepts(COAST));
        day.add(COAST);

        assertFalse(day.accepts("A01011700"), "자연이 넷째면 바다·산만 도는 하루가 된다");
    }

    /** 계열이 다르면 셋째도 들어간다 — 다양성이 목적이지 제약이 목적이 아니다. */
    @Test
    void 계열이_다르면_막지_않는다() {
        SightVariety day = SightVariety.strict();

        day.add(BEACH);
        day.add(PORT);
        day.add(COAST);

        assertTrue(day.accepts(RELIC), "자연 셋이 찼다고 역사까지 막으면 코스가 안 만들어진다");
    }

    /** 후보가 얇으면 물러난다 — 정선·신안은 상한을 고집하면 동선이 +20㎞ 늘거나 슬롯이 빈다. */
    @Test
    void 풀면_같은_종류도_더_받는다() {
        SightVariety relaxed = SightVariety.relaxedBy(1);

        relaxed.add(BEACH);

        assertTrue(relaxed.accepts(BEACH), "빈 슬롯이 중복보다 나쁘다");
    }

    /**
     * <b>종류를 모르면 막지 않는다.</b> 우리 DB 출처(인허가·국가유산)는 이 값이 없는데, 모르는 것끼리
     * 한 칸으로 묶으면 그것들이 하루에 하나밖에 못 들어간다.
     */
    @Test
    void 종류를_모르면_막지_않는다() {
        SightVariety day = SightVariety.strict();

        day.add(null);
        day.add(null);

        assertTrue(day.accepts(null));
        assertTrue(day.accepts(""));
    }
}
