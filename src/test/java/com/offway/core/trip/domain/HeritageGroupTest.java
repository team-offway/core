package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 국가유산 대분류(#160) — <b>갈 수 있는 곳인가</b> 를 가르는 규칙.
 *
 * <p>이 판정이 틀리면 코스에 그림 한 점·판소리 한 마당이 목적지로 들어간다. 실제로 우리 89곳의 국보·보물
 * 표본 12건이 전부 소장 유물이었다 — 종목으로는 안 갈린다.
 */
class HeritageGroupTest {

    @ParameterizedTest
    @ValueSource(strings = {"유적건조물", "자연유산", "등록문화유산"})
    void 그_자리에_가는_것이_관람이면_방문_가능이다(String label) {
        assertTrue(HeritageGroup.from(label).orElseThrow().isVisitable());
    }

    @ParameterizedTest
    @ValueSource(strings = {"유물", "기록유산", "무형유산"})
    void 소장품과_기능은_방문_대상이_아니다(String label) {
        // 유물·기록유산은 주소가 소장 기관이라 그림이 목적지가 되고, 무형유산은 보유자 소재지라 장소가 아니다.
        assertFalse(HeritageGroup.from(label).orElseThrow().isVisitable());
    }

    @Test
    void 이름으로_찾는다() {
        assertEquals(Optional.of(HeritageGroup.HISTORIC_STRUCTURE), HeritageGroup.from("유적건조물"));
        assertEquals(Optional.of(HeritageGroup.NATURAL), HeritageGroup.from(" 자연유산 "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "우주유산"})
    void 모르는_분류는_비어_있음이다(String unknown) {
        // 제공기관이 분류를 늘리면 그 건은 코스에 안 쓰이고 적재 로그에 건수가 남는다.
        // 실제 수집분 6,387건 중 23건이 대분류가 빈 채로 왔다 — 조용히 섞여 들어가는 쪽이 나쁘다.
        assertTrue(HeritageGroup.from(unknown).isEmpty());
    }

    @Test
    void null_도_비어_있음이다() {
        assertTrue(HeritageGroup.from(null).isEmpty());
    }
}
