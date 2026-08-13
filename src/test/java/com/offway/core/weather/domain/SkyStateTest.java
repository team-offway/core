package com.offway.core.weather.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 날씨 상태 6종 판정(#135). 화면 아이콘과 1:1 이라 틀리면 사용자가 바로 본다.
 */
class SkyStateTest {

    @ParameterizedTest(name = "SKY={0} PTY={1} → {2}")
    @CsvSource({
            // 강수가 없으면 하늘이 답이다
            "1, 0, CLEAR",
            "3, 0, PARTLY_CLOUDY",
            "4, 0, CLOUDY",
            // 강수가 있으면 하늘을 덮는다 — 맑은데 비가 와도 비다
            "1, 1, RAIN",
            "3, 1, RAIN",
            "4, 1, RAIN",
            "1, 2, SLEET",
            "1, 3, SNOW",
            // 소나기는 비로 묶는다 (아이콘 6종)
            "1, 4, RAIN",
            "4, 4, RAIN",
    })
    void 단기예보는_강수가_하늘을_덮는다(String sky, String pty, SkyState expected) {
        assertEquals(expected, SkyState.from(sky, pty));
    }

    @Test
    void 강수코드를_모르면_하늘로_떨어진다() {
        // PTY 가 안 왔거나 모르는 값이면 하늘 상태가 답이어야 한다 — 조용히 UNKNOWN 이 되면 아이콘이 사라진다.
        assertEquals(SkyState.CLOUDY, SkyState.from("4", null));
        assertEquals(SkyState.CLOUDY, SkyState.from("4", "9"));
        assertEquals(SkyState.UNKNOWN, SkyState.from(null, null));
    }

    @ParameterizedTest(name = "중기 \"{0}\" → {1}")
    @CsvSource({
            "맑음, CLEAR",
            "구름많음, PARTLY_CLOUDY",
            "구름조금, PARTLY_CLOUDY",
            "흐림, CLOUDY",
            // 예전에는 앞부분만 떼고 강수를 버려 "흐리고 비" 가 흐림으로 나갔다
            "구름많고 비, RAIN",
            "흐리고 비, RAIN",
            "구름많고 눈, SNOW",
            "흐리고 눈, SNOW",
            "구름많고 소나기, RAIN",
            "흐리고 소나기, RAIN",
    })
    void 중기예보_문구에서_강수를_살린다(String text, SkyState expected) {
        assertEquals(expected, SkyState.fromMidTermText(text));
    }

    @ParameterizedTest(name = "\"{0}\" 은 눈비")
    @ValueSource(strings = {"구름많고 비/눈", "흐리고 비/눈", "구름많고 눈/비"})
    void 눈비는_비로_뭉개지지_않는다(String text) {
        // "비/눈" 이 "비" 를 포함하므로 순서를 잘못 잡으면 눈비가 비로 떨어진다.
        assertEquals(SkyState.SLEET, SkyState.fromMidTermText(text));
    }

    @Test
    void 모르는_문구는_정보없음이다() {
        assertEquals(SkyState.UNKNOWN, SkyState.fromMidTermText("황사"));
        assertEquals(SkyState.UNKNOWN, SkyState.fromMidTermText(null));
        assertEquals(SkyState.UNKNOWN, SkyState.fromMidTermText("  "));
    }

    @Test
    void 오전_오후를_합칠_때_나쁜_쪽을_남긴다() {
        // "오전 맑고 오후 비" 를 맑음으로 뭉개면 우산을 안 챙긴다.
        assertEquals(SkyState.RAIN, SkyState.CLEAR.worseOf(SkyState.RAIN));
        assertEquals(SkyState.RAIN, SkyState.RAIN.worseOf(SkyState.CLEAR));
        assertEquals(SkyState.SLEET, SkyState.SNOW.worseOf(SkyState.SLEET));
        assertEquals(SkyState.CLOUDY, SkyState.CLOUDY.worseOf(SkyState.PARTLY_CLOUDY));
    }

    @Test
    void 모름은_비교에서_빠진다() {
        assertEquals(SkyState.CLEAR, SkyState.CLEAR.worseOf(SkyState.UNKNOWN));
        assertEquals(SkyState.CLEAR, SkyState.UNKNOWN.worseOf(SkyState.CLEAR));
        assertEquals(SkyState.UNKNOWN, SkyState.UNKNOWN.worseOf(SkyState.UNKNOWN));
    }

    @Test
    void 강수_여부를_스스로_안다() {
        assertTrue(SkyState.RAIN.hasPrecipitation());
        assertTrue(SkyState.SNOW.hasPrecipitation());
        assertTrue(SkyState.SLEET.hasPrecipitation());
        assertFalse(SkyState.CLOUDY.hasPrecipitation());
        assertFalse(SkyState.UNKNOWN.hasPrecipitation());
    }

    @Test
    void 화면_아이콘_여섯_종에_대응한다() {
        // FE 가 준비한 아이콘과 1:1 이다. 라벨이 바뀌면 아이콘 매핑이 조용히 깨진다.
        assertEquals("맑음", SkyState.CLEAR.label());
        assertEquals("구름많음", SkyState.PARTLY_CLOUDY.label());
        assertEquals("흐림", SkyState.CLOUDY.label());
        assertEquals("비", SkyState.RAIN.label());
        assertEquals("눈", SkyState.SNOW.label());
        assertEquals("눈비", SkyState.SLEET.label());
    }
}
