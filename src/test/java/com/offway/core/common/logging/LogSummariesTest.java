package com.offway.core.common.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class LogSummariesTest {

    @Test
    void 다섯건_이하면_잘림표시를_붙이지_않는다() {
        List<String> items = List.of("a", "b", "c", "d", "e");

        assertEquals("추천=5건 (a b c d e)", LogSummaries.list("추천", items, s -> s));
    }

    @Test
    void 네건이면_전부_낸다() {
        assertEquals("추천=4건 (a b c d)", LogSummaries.list("추천", List.of("a", "b", "c", "d"), s -> s));
    }

    @Test
    void 여섯건이면_다섯건과_잘림표시를_낸다() {
        List<String> items = List.of("a", "b", "c", "d", "e", "f");

        assertEquals("추천=6건 (a b c d e …외 1건)", LogSummaries.list("추천", items, s -> s));
    }

    @Test
    void 스무건이면_다섯건과_외15건을_낸다() {
        List<String> items = java.util.stream.IntStream.rangeClosed(1, 20)
                .mapToObj(String::valueOf)
                .toList();

        assertEquals("추천=20건 (1 2 3 4 5 …외 15건)", LogSummaries.list("추천", items, s -> s));
    }

    @Test
    void 빈_목록은_건수0과_빈_괄호다() {
        assertEquals("추천=0건 ()", LogSummaries.list("추천", List.of(), s -> s.toString()));
    }

    @Test
    void null_목록은_건수0으로_본다() {
        assertEquals("추천=0건 ()", LogSummaries.list("추천", null, Object::toString));
    }

    @Test
    void describe_가_붙인_설명이_그대로_들어간다() {
        record Region(long id, String name, double score) {}
        List<Region> items = List.of(new Region(76, "영월", 1.0), new Region(7, "삼척", 0.94));

        assertEquals(
                "추천=2건 (76:영월1.00 7:삼척0.94)",
                LogSummaries.list("추천", items, r -> "%d:%s%.2f".formatted(r.id(), r.name(), r.score())));
    }
}
