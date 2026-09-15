package com.offway.core.transport.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.geo.Coordinate;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 같은 곳에 내리는 둘 중 도착 시각을 아는 쪽을 고르는가(#551).
 *
 * <p><b>이 판정이 틀리면 동선이 바뀐다.</b> 같은 곳이 아닌데 바꾸면 사용자가 다른 터미널에 내리고,
 * 실제로 다른 지역으로 넘어가는 사례가 있다(연천역→철원터미널). 그래서 "안 바꾸는 쪽" 을 더 촘촘히
 * 잠근다.
 */
class SameArrivalPointTest {

    /** 양양 종합터미널 — 고속·시외가 같은 자리다. 좌표는 미세하게 갈린다. */
    private static final Coordinate YANGYANG = new Coordinate(38.0754, 128.6190);

    private static Terminal express(Coordinate at) {
        return new Terminal("EXP-1", "양양", BusTerminalKind.EXPRESS, at, true);
    }

    private static Terminal intercity(Coordinate at) {
        return new Terminal("INT-1", "양양", BusTerminalKind.INTERCITY, at, true);
    }

    private static Coordinate nearby(double km) {
        // 위도 1도 ≈ 111km. 남쪽으로 km 만큼 옮긴다.
        return new Coordinate(YANGYANG.lat() - km / 111.0, YANGYANG.lng());
    }

    @Test
    void 같은_자리면_같은_곳으로_본다() {
        assertTrue(SameArrivalPoint.is(express(YANGYANG), intercity(YANGYANG)));
    }

    /** 종합터미널의 고속·시외 좌표가 미세하게 갈리는 것을 덮어야 한다. */
    @Test
    void 백미터_차이는_같은_곳이다() {
        assertTrue(SameArrivalPoint.is(express(YANGYANG), intercity(nearby(0.1))));
    }

    /** 길 건너 다른 터미널까지 묶으면 안 된다. */
    @Test
    void 일킬로_떨어지면_다른_곳이다() {
        assertFalse(SameArrivalPoint.is(express(YANGYANG), intercity(nearby(1.0))));
    }

    @Test
    void 한쪽이_없으면_견줄_것이_없다() {
        assertFalse(SameArrivalPoint.is(express(YANGYANG), null));
        assertFalse(SameArrivalPoint.is(null, intercity(YANGYANG)));
    }

    /** <b>이 PR 의 핵심 분기</b> — 같은 곳이고 고속만 시각을 알면 고속으로 바꾼다. */
    @Test
    void 같은_곳이면_시각을_아는_쪽을_고른다() {
        Terminal e = express(YANGYANG);
        Terminal i = intercity(nearby(0.05));

        Optional<Terminal> chosen = SameArrivalPoint.timeAware(
                Optional.of(i), Optional.of(e), Optional.of(i), knows(e));

        assertEquals(e, chosen.orElseThrow(), "시각을 아는 쪽으로 바뀌어야 한다");
    }

    /** 반대 방향도 같다 — 고속이 대표인데 시외만 알면 시외로. */
    @Test
    void 반대_방향도_바꾼다() {
        Terminal e = express(YANGYANG);
        Terminal i = intercity(nearby(0.05));

        Optional<Terminal> chosen = SameArrivalPoint.timeAware(
                Optional.of(e), Optional.of(e), Optional.of(i), knows(i));

        assertEquals(i, chosen.orElseThrow());
    }

    /**
     * <b>지점이 다르면 안 바꾼다.</b> 바꾸는 순간 동선이 변하고, 다른 지역으로 넘어가는 사례가 있다.
     */
    @Test
    void 지점이_다르면_시각을_알아도_안_바꾼다() {
        Terminal e = express(nearby(5.0));
        Terminal i = intercity(YANGYANG);

        Optional<Terminal> chosen = SameArrivalPoint.timeAware(
                Optional.of(i), Optional.of(e), Optional.of(i), knows(e));

        assertEquals(i, chosen.orElseThrow(), "먼 터미널로 바꾸면 사용자가 엉뚱한 곳에 내린다");
    }

    @Test
    void 둘_다_알면_그대로_둔다() {
        Terminal e = express(YANGYANG);
        Terminal i = intercity(nearby(0.05));

        Optional<Terminal> chosen = SameArrivalPoint.timeAware(
                Optional.of(i), Optional.of(e), Optional.of(i), knows(e, i));

        assertEquals(i, chosen.orElseThrow(), "바꿔도 얻는 것이 없다");
    }

    @Test
    void 둘_다_모르면_그대로_둔다() {
        Terminal e = express(YANGYANG);
        Terminal i = intercity(nearby(0.05));

        Optional<Terminal> chosen = SameArrivalPoint.timeAware(
                Optional.of(i), Optional.of(e), Optional.of(i), knows());

        assertEquals(i, chosen.orElseThrow());
    }

    /** 한 종류만 있는 지역은 견줄 것이 없다 — 그대로 둔다. */
    @Test
    void 한_종류만_있으면_그대로_둔다() {
        Terminal i = intercity(YANGYANG);

        assertEquals(i, SameArrivalPoint.timeAware(
                Optional.of(i), Optional.empty(), Optional.of(i), knows(i)).orElseThrow());
        assertEquals(i, SameArrivalPoint.timeAware(
                Optional.of(i), Optional.of(i), Optional.empty(), knows(i)).orElseThrow());
    }

    /** 대표가 없으면 만들어 내지 않는다. */
    @Test
    void 대표가_없으면_비운_채_둔다() {
        Terminal e = express(YANGYANG);
        Terminal i = intercity(YANGYANG);

        assertTrue(SameArrivalPoint.timeAware(
                Optional.empty(), Optional.of(e), Optional.of(i), knows(e)).isEmpty());
    }

    /** 시각을 아는 터미널 집합 — 조회는 서비스가 소유하므로 여기서는 코드로 흉내 낸다. */
    private static java.util.function.Predicate<Terminal> knows(Terminal... known) {
        Set<String> codes = Set.of(java.util.Arrays.stream(known).map(Terminal::code).toArray(String[]::new));
        return terminal -> codes.contains(terminal.code());
    }
}
