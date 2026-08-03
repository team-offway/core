package com.offway.core.weather.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 하늘 상태 해석 — 단기예보는 숫자 코드, 중기예보는 한글 문구로 온다.
 *
 * <p>중기 문구는 {@code "구름많고 비"}·{@code "흐리고 눈"} 처럼 <b>하늘 상태에 강수가 덧붙은 복합형</b>이라
 * 완전일치로는 못 잡는다. 접두사로 가르는 이유이자, 새 문구가 와도 조용히 사라지지 않아야 하는 이유다.
 */
class SkyStateTest {

    @ParameterizedTest
    @CsvSource({"1, CLEAR", "3, PARTLY_CLOUDY", "4, CLOUDY"})
    void 단기예보_코드를_하늘상태로_옮긴다(String code, SkyState expected) {
        assertEquals(expected, SkyState.fromCode(code));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "0", "9", "x"})
    void 모르는_단기예보_코드는_정보없음이다(String code) {
        // 2 번(구름조금)은 기상청이 폐지했다. 되살아나도 잘못된 값으로 단정하지 않는다.
        assertEquals(SkyState.UNKNOWN, SkyState.fromCode(code));
    }

    @ParameterizedTest
    @CsvSource({
        "맑음, CLEAR",
        "구름많음, PARTLY_CLOUDY",
        "구름조금, PARTLY_CLOUDY",
        "흐림, CLOUDY",
    })
    void 중기예보_문구를_하늘상태로_옮긴다(String text, SkyState expected) {
        assertEquals(expected, SkyState.fromMidTermText(text));
    }

    @ParameterizedTest
    @CsvSource({
        "구름많고 비, PARTLY_CLOUDY",
        "구름많고 눈, PARTLY_CLOUDY",
        "흐리고 비, CLOUDY",
        "흐리고 눈, CLOUDY",
        "흐리고 비/눈, CLOUDY",
    })
    void 강수가_덧붙은_복합_문구도_하늘_상태를_잃지_않는다(String text, SkyState expected) {
        // 완전일치로 잡으면 이 문구들이 전부 UNKNOWN 이 되어 하늘 상태가 통째로 사라진다.
        assertEquals(expected, SkyState.fromMidTermText(text));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "알 수 없음"})
    void 비었거나_모르는_문구는_정보없음이다(String text) {
        assertEquals(SkyState.UNKNOWN, SkyState.fromMidTermText(text));
    }

    @Test
    void 오전_오후_중_더_흐린_쪽을_고른다() {
        // 맑은 쪽을 대표로 삼으면 실제보다 좋게 안내돼 사용자가 우산을 안 챙긴다.
        assertEquals(SkyState.CLOUDY, SkyState.CLEAR.worseOf(SkyState.CLOUDY));
        assertEquals(SkyState.PARTLY_CLOUDY, SkyState.PARTLY_CLOUDY.worseOf(SkyState.CLEAR));
    }

    @Test
    void 한쪽만_모르면_아는_쪽을_쓰고_둘_다_모르면_모름이다() {
        assertEquals(SkyState.CLEAR, SkyState.UNKNOWN.worseOf(SkyState.CLEAR));
        assertEquals(SkyState.CLEAR, SkyState.CLEAR.worseOf(SkyState.UNKNOWN));
        assertEquals(SkyState.UNKNOWN, SkyState.UNKNOWN.worseOf(SkyState.UNKNOWN));
    }
}
