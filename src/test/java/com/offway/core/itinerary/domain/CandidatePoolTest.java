package com.offway.core.itinerary.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.transport.domain.Coordinate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CandidatePoolTest {

    /** 운영에서 실제로 걸린 좌표 — 귀목봉(가평, 해발 1,036m). 근처에 도로가 없어 TMAP 이 거절한다. */
    private static final Coordinate GWIMOK = new Coordinate(37.9419179195, 127.3836649009);

    /** 운영 코스 67(평창) 3일차에 슬롯 넷이 함께 앉아 있던 좌표 — 알펜시아 리조트. */
    private static final Coordinate ALPENSIA = new Coordinate(37.6541478, 128.652815);

    private static final Coordinate A = new Coordinate(37.10, 127.10);
    private static final Coordinate B = new Coordinate(37.20, 127.20);

    private static Predicate<Coordinate> blocked(Coordinate... points) {
        Set<Coordinate> blocked = new HashSet<>(List.of(points));
        return blocked::contains;
    }

    /** 아무것도 안 막는 술어 — "차단이 없으면 그대로" 를 읽기 쉽게 쓰려고 이름을 준다. */
    private static final Predicate<Coordinate> NOTHING_BLOCKED = point -> false;

    // ── ① 경로를 못 만드는 좌표 ────────────────────────────────────────────

    @Test
    void 차단된_좌표는_빠진다() {
        List<Coordinate> pool = List.of(A, GWIMOK, B);

        assertEquals(List.of(0, 2), CandidatePool.usable(pool, blocked(GWIMOK), 0));
    }

    /**
     * 좌표로 거르는 이유가 이것이다. 인허가 풀은 31.4%(38,136건)가 다른 장소와 좌표를 공유해, 같은 자리의
     * 다른 장소도 <b>같은 이유로</b> 실패한다.
     */
    @Test
    void 차단된_좌표에_앉은_다른_후보도_함께_빠진다() {
        List<Coordinate> pool = List.of(A, GWIMOK, GWIMOK, B);

        assertEquals(List.of(0, 3), CandidatePool.usable(pool, blocked(GWIMOK), 0));
    }

    @Test
    void 차단_목록이_비면_아무것도_빼지_않는다() {
        List<Coordinate> pool = List.of(A, GWIMOK, B);

        assertEquals(List.of(0, 1, 2), CandidatePool.usable(pool, NOTHING_BLOCKED, 0));
    }

    @Test
    void 전부_차단되면_빈_목록이다() {
        assertTrue(CandidatePool.usable(List.of(GWIMOK, GWIMOK), blocked(GWIMOK), 0).isEmpty());
    }

    // ── ② 같은 좌표 접기 ──────────────────────────────────────────────────

    /** 넷이 한 점에 있으면 화면에 "이동 0분" 슬롯이 네 개 연속으로 뜬다. 하나만 남긴다. */
    @Test
    void 같은_좌표의_후보는_하나만_남는다() {
        List<Coordinate> pool = List.of(ALPENSIA, ALPENSIA, ALPENSIA, ALPENSIA);

        assertEquals(1, CandidatePool.usable(pool, NOTHING_BLOCKED, 0).size());
    }

    @Test
    void 다른_좌표는_그대로_다_남는다() {
        assertEquals(List.of(0, 1, 2), CandidatePool.usable(List.of(A, B, GWIMOK), NOTHING_BLOCKED, 0));
    }

    /** 남는 자리의 순서는 입력 순서를 따른다 — 뒤에 붙는 정렬·군집이 기대하는 성질이다. */
    @Test
    void 남은_것들의_상대_순서가_유지된다() {
        List<Coordinate> pool = List.of(B, ALPENSIA, A, ALPENSIA);

        List<Integer> usable = CandidatePool.usable(pool, NOTHING_BLOCKED, 0);

        assertEquals(List.of(0, 1, 2), usable);
    }

    /**
     * 늘 첫 번째를 남기면 재생성(#114)이 같은 자리에서 다른 가게를 못 뽑아 "다른 코스" 가 그만큼 좁아진다.
     * 씨앗이 바뀌면 같은 좌표 무리에서 다른 후보가 나와야 한다.
     */
    @Test
    void 씨앗이_다르면_같은_좌표에서_다른_후보가_뽑힌다() {
        List<Coordinate> pool = List.of(ALPENSIA, ALPENSIA, ALPENSIA);

        Set<Integer> picked = LongStream.range(0, 3)
                .mapToObj(seed -> CandidatePool.usable(pool, NOTHING_BLOCKED, seed).getFirst())
                .collect(HashSet::new, HashSet::add, HashSet::addAll);

        assertEquals(Set.of(0, 1, 2), picked, "무리 크기만큼의 씨앗이면 후보를 전부 훑어야 한다");
    }

    /** 씨앗은 재생성이 임의로 넘기는 값이라 음수·아주 큰 값도 온다. 어떤 값이든 유효한 인덱스여야 한다. */
    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, -7, -1, 0, 1, 8_123_456_789L, Long.MAX_VALUE})
    void 어떤_씨앗이든_유효한_후보를_고른다(long seed) {
        List<Coordinate> pool = List.of(ALPENSIA, ALPENSIA, A);

        List<Integer> usable = CandidatePool.usable(pool, NOTHING_BLOCKED, seed);

        assertEquals(2, usable.size());
        assertTrue(usable.getFirst() >= 0 && usable.getFirst() <= 1, "실제=" + usable.getFirst());
        assertEquals(2, usable.get(1));
    }

    // ── 둘이 함께 ─────────────────────────────────────────────────────────

    @Test
    void 차단과_접기가_함께_적용된다() {
        List<Coordinate> pool = List.of(GWIMOK, ALPENSIA, ALPENSIA, A, GWIMOK);

        List<Integer> usable = CandidatePool.usable(pool, blocked(GWIMOK), 0);

        assertEquals(List.of(1, 3), usable);
    }

    @Test
    void 빈_풀은_빈_목록이다() {
        assertTrue(CandidatePool.usable(List.of(), blocked(GWIMOK), 0).isEmpty());
    }
}
