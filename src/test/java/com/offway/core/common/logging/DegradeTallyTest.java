package com.offway.core.common.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import java.util.stream.Stream;
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

        assertEquals(3, tally.snapshot().total());
    }

    @Test
    void 요약은_많은_사유부터_보여준다() {
        DegradeTally tally = new DegradeTally();
        tally.add("TimeoutException");
        IntStream.range(0, 5).forEach(i -> tally.add("429"));

        assertEquals("429=5, TimeoutException=1", tally.snapshot().summary());
    }

    @Test
    void 로그_조각은_괄호로_감싼다() {
        // 요약 줄 뒤에 그대로 이어붙이는 조각이다. 호출부마다 괄호를 조립하면 표기가 갈린다.
        DegradeTally tally = new DegradeTally();
        tally.add("429");

        assertEquals("(429=1)", tally.snapshot().summaryFragment());
    }

    @Test
    void 실패가_없으면_로그_조각도_없다() {
        // 빈 괄호 "()" 가 붙으면 실패가 있었는데 사유를 못 적은 것처럼 읽힌다.
        assertEquals("", new DegradeTally().snapshot().summaryFragment());
    }

    @Test
    void 아무것도_없으면_요약도_비어_있다() {
        assertEquals("", new DegradeTally().snapshot().summary());
        assertEquals(0, new DegradeTally().snapshot().total());
    }

    @Test
    void 팬아웃에서_동시에_세도_총계가_맞는다() throws Exception {
        // 지역 콘텐츠는 89곳을 병렬로 돈다. 카운터가 경쟁하면 요약이 조용히 틀린다.
        DegradeTally tally = new DegradeTally();

        CompletableFuture.allOf(IntStream.range(0, 200)
                        .mapToObj(i -> CompletableFuture.runAsync(() -> tally.add(i % 2 == 0 ? "429" : "timeout")))
                        .toArray(CompletableFuture[]::new))
                .get();

        DegradeTally.Snapshot snapshot = tally.snapshot();
        assertEquals(200, snapshot.total());
        assertEquals("429=100, timeout=100", snapshot.summary());
    }

    @Test
    void 세는_중에_읽어도_총계와_사유별_합계가_어긋나지_않는다() throws Exception {
        // 팬아웃 마감을 넘긴 작업은 계속 돌면서 add 를 부른다. 총계와 요약을 따로 읽으면 그 사이에
        // 한 건이 끼어들어 "degrade 5건(429=4)" 처럼 서로 안 맞는 로그가 나간다.
        DegradeTally tally = new DegradeTally();
        CompletableFuture<Void> counting = CompletableFuture.allOf(IntStream.range(0, 500)
                .mapToObj(i -> CompletableFuture.runAsync(() -> tally.add(i % 3 == 0 ? "429" : "timeout")))
                .toArray(CompletableFuture[]::new));

        while (!counting.isDone()) {
            DegradeTally.Snapshot snapshot = tally.snapshot();
            assertEquals(snapshot.total(), sumOfReasons(snapshot.summary()),
                    "총계와 사유별 합계가 어긋났다. 요약=" + snapshot.summary() + " 총계=" + snapshot.total());
        }
        counting.get();
    }

    /** {@code 429=4, timeout=1} 의 값들을 더한다 — 요약이 스스로와 맞는지 보는 용도. */
    private static int sumOfReasons(String summary) {
        if (summary.isEmpty()) {
            return 0;
        }
        return Stream.of(summary.split(", "))
                .mapToInt(entry -> Integer.parseInt(entry.split("=")[1]))
                .sum();
    }
}
