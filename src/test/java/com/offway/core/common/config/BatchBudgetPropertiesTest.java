package com.offway.core.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 배치가 한 회차에 만질 지역 수(#254).
 *
 * <p>로컬과 운영이 같은 외부 API 키를 쓰는데 배치 건너뛰기는 자기 DB 안에서만 유효해, 두 곳이 각자
 * 하루치를 태웠다. 로컬만 회차당 처리량을 줄여 대응한다.
 */
class BatchBudgetPropertiesTest {

    private static final List<String> NINE = List.of("1", "2", "3", "4", "5", "6", "7", "8", "9");

    @Test
    void 예산_안이면_그대로_준다() {
        assertEquals(NINE, new BatchBudgetProperties(10).limit(NINE));
    }

    @Test
    void 예산을_넘으면_앞에서부터_자른다() {
        // 앞에서부터인 것이 중요하다 — 무작위면 회차마다 다른 지역이 걸려 로컬에서
        // "어제 보이던 데이터가 오늘은 없다" 가 된다.
        assertEquals(List.of("1", "2", "3"), new BatchBudgetProperties(3).limit(NINE));
    }

    /**
     * <b>운영 기본값이다.</b> 여기가 틀어지면 운영이 조용히 몇 곳만 갱신하고, 나머지 지역 카드가
     * 옛 값으로 굳는다 — 화면은 멀쩡해 보여 아무도 모른다.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void 제한없음이면_전_지역을_그대로_준다(int noLimit) {
        assertSame(NINE, new BatchBudgetProperties(noLimit).limit(NINE));
        assertFalse(new BatchBudgetProperties(noLimit).limits(NINE.size()));
    }

    @Test
    void 잘렸는지를_알려준다() {
        // 로그에 남겨 "왜 89곳이 아닌가" 를 다시 조사하지 않게 한다.
        assertTrue(new BatchBudgetProperties(10).limits(89));
        assertFalse(new BatchBudgetProperties(10).limits(10));
        assertFalse(new BatchBudgetProperties(10).limits(3));
    }

    @Test
    void 빈_목록도_그대로_준다() {
        assertEquals(List.of(), new BatchBudgetProperties(10).limit(List.of()));
    }
}
