package com.offway.core.common.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * degrade 를 사유별로 센다. 89개 지역이 같은 이유로 실패하면 로그는 한 줄이어야 하고, 요약은 "몇 건이 어떤
 * 이유였나" 에 답해야 한다(#224).
 */
class DegradeTallyTest {

    @Test
    void 사유가_처음일_때만_참을_돌려준다() {
        // 이 값이 곧 "이 건을 WARN 으로 남길까" 다. 39건이 같은 이유면 WARN 은 한 줄이면 충분하다.
        DegradeTally tally = new DegradeTally();

        assertTrue(tally.add("429"), "첫 건은 남겨야 사유를 알 수 있다");
        assertFalse(tally.add("429"), "같은 이유의 두 번째부터는 같은 말이다");
        assertFalse(tally.add("429"));
    }

    @Test
    void 사유가_다르면_그것도_처음이다() {
        // 429 39건에 묻혀 timeout 1건을 놓치면, 진짜 원인이 그 하나일 때 못 찾는다.
        DegradeTally tally = new DegradeTally();
        tally.add("429");

        assertTrue(tally.add("TimeoutException"), "다른 이유는 따로 한 번 남긴다");
    }

    @Test
    void 총계는_사유와_무관하게_전부_센다() {
        DegradeTally tally = new DegradeTally();
        tally.add("429");
        tally.add("429");
        tally.add("TimeoutException");

        assertEquals(3, tally.total());
    }

    @Test
    void 요약은_많은_사유부터_보여준다() {
        DegradeTally tally = new DegradeTally();
        tally.add("TimeoutException");
        IntStream.range(0, 5).forEach(i -> tally.add("429"));

        assertEquals("429=5, TimeoutException=1", tally.summary());
    }

    @Test
    void 로그_조각은_괄호로_감싼다() {
        // 요약 줄 뒤에 그대로 이어붙이는 조각이다. 호출부마다 괄호를 조립하면 표기가 갈린다.
        DegradeTally tally = new DegradeTally();
        tally.add("429");

        assertEquals("(429=1)", tally.summaryFragment());
    }

    @Test
    void 실패가_없으면_로그_조각도_없다() {
        // 빈 괄호 "()" 가 붙으면 실패가 있었는데 사유를 못 적은 것처럼 읽힌다.
        assertEquals("", new DegradeTally().summaryFragment());
    }

    @Test
    void 아무것도_없으면_요약도_비어_있다() {
        assertEquals("", new DegradeTally().summary());
        assertEquals(0, new DegradeTally().total());
    }

    @Test
    void 팬아웃에서_동시에_세도_총계가_맞는다() throws Exception {
        // 지역 콘텐츠는 89곳을 병렬로 돈다. 카운터가 경쟁하면 요약이 조용히 틀린다.
        DegradeTally tally = new DegradeTally();

        CompletableFuture.allOf(IntStream.range(0, 200)
                        .mapToObj(i -> CompletableFuture.runAsync(() -> tally.add(i % 2 == 0 ? "429" : "timeout")))
                        .toArray(CompletableFuture[]::new))
                .get();

        assertEquals(200, tally.total());
        assertEquals("429=100, timeout=100", tally.summary());
    }
}
