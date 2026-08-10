package com.offway.core.common.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 요청 로그에 실을 파라미터 렌더링. 가려야 할 값을 드러내지 않으면서 사람이 읽을 수 있어야 한다.
 */
class SensitiveParamsTest {

    @Test
    void 민감하지_않은_파라미터는_그대로_남긴다() {
        assertEquals("regionId=31, days=3", SensitiveParams.readableParams("regionId=31&days=3"));
    }

    @Test
    void 한글은_디코딩해서_읽히게_한다() {
        // 원문은 %ec%b6%a9... 이라 눈으로 못 읽는다. 이게 이 메서드가 생긴 이유다.
        assertEquals("region=충청남도",
                SensitiveParams.readableParams("region=%ec%b6%a9%ec%b2%ad%eb%82%a8%eb%8f%84"));
    }

    @Test
    void 민감한_이름의_값은_가린다() {
        assertEquals("serviceKey=***, regionId=31",
                SensitiveParams.readableParams("serviceKey=abc123&regionId=31"));
    }

    @Test
    void 이름_대소문자를_가리지_않는다() {
        assertEquals("SERVICEKEY=***", SensitiveParams.readableParams("SERVICEKEY=abc123"));
        assertEquals("appkey=***", SensitiveParams.readableParams("appkey=xyz"));
    }

    @Test
    void 민감한_이름_넷을_모두_가린다() {
        assertEquals("serviceKey=***, appKey=***, password=***, token=***",
                SensitiveParams.readableParams("serviceKey=a&appKey=b&password=c&token=d"));
    }

    @Test
    void 퍼센트_인코딩된_이름도_가린다() {
        // 이름을 인코딩해 마스킹을 피해가지 못하게 한다.
        assertEquals("token=***", SensitiveParams.readableParams("t%6Fken=secret"));
    }

    @Test
    void 값이_없는_파라미터도_깨지지_않는다() {
        assertEquals("mood=, regionId=31", SensitiveParams.readableParams("mood=&regionId=31"));
    }

    @Test
    void 등호가_없는_조각은_이름만_남긴다() {
        assertEquals("flag, regionId=31", SensitiveParams.readableParams("flag&regionId=31"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 쿼리가_없으면_빈_문자열이다(String query) {
        assertEquals("", SensitiveParams.readableParams(query));
    }

    @Test
    void 깨진_퍼센트_인코딩이어도_예외_없이_통과한다() {
        // 로그를 찍다가 요청 처리가 죽으면 안 된다.
        assertEquals("regionId=%ZZ", SensitiveParams.readableParams("regionId=%ZZ"));
    }

    @Test
    void 개행을_넣어_가짜_로그_줄을_만들_수_없다() {
        // 디코딩하는 순간 생기는 위험이다 — %0A 가 실제 개행이 되면 값 하나가 로그를 여러 줄로 쪼갠다.
        // 인코딩된 채로 찍던 시절에는 없던 문제라 디코딩과 함께 막아야 한다.
        String rendered = SensitiveParams.readableParams("q=hello%0A2026-01-01%20INFO%20%20fake");

        assertFalse(rendered.contains("\n"), "개행이 남으면 로그 줄이 쪼개진다. 실제=" + rendered);
        assertFalse(rendered.contains("\r"), "실제=" + rendered);
        assertEquals("q=hello2026-01-01 INFO  fake", rendered);
    }

    @Test
    void 탭도_걸러낸다() {
        assertEquals("q=ab", SensitiveParams.readableParams("q=a%09b"));
    }

    @Test
    void 아주_긴_값은_잘라낸다() {
        // 긴 값 하나가 줄 전체를 밀어내면 나머지를 못 읽는다.
        String rendered = SensitiveParams.readableParams("q=" + "가".repeat(200));

        assertTrue(rendered.endsWith("…"), "잘렸다는 표식이 있어야 한다. 실제 길이=" + rendered.length());
        assertTrue(rendered.length() < 80, "실제 길이=" + rendered.length());
    }

    @Test
    void 제어문자를_끼워_마스킹을_피할_수_없다() {
        // to%0Aken 은 원문 기준으로 개행이 낀 이름이라 "token" 과 안 맞는데, 렌더링은 제어문자를 지운다.
        // 판정과 표시가 갈리면 값이 그대로 나간다 — 판정도 표시될 이름으로 해야 한다.
        assertEquals("token=***", SensitiveParams.readableParams("to%0Aken=secret"));
    }

    @Test
    void 자유_텍스트의_비밀값을_가린다() {
        // 예외 메시지에 요청 URL 이 통째로 들어오는 경우가 있다. 우리 외부 호출은 serviceKey 를 쿼리에 싣는다.
        String message = "429 from GET https://apis.data.go.kr/x?serviceKey=abc123&areaCode=34";

        String masked = SensitiveParams.maskSecretsInText(message);

        assertFalse(masked.contains("abc123"), "실제=" + masked);
        assertTrue(masked.contains("serviceKey=***"), "실제=" + masked);
        assertTrue(masked.contains("areaCode=34"), "민감하지 않은 값은 남아야 한다. 실제=" + masked);
    }

    @Test
    void 자유_텍스트가_비어도_깨지지_않는다() {
        assertEquals("", SensitiveParams.maskSecretsInText(""));
        assertEquals(null, SensitiveParams.maskSecretsInText(null));
    }
}
