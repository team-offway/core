package com.offway.core.common.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SensitiveParamsTest {

    @Test
    void 민감하지_않은_파라미터는_그대로_남긴다() {
        assertEquals("regionId=31&days=3", SensitiveParams.maskQueryString("regionId=31&days=3"));
    }

    @Test
    void 민감한_이름의_값은_가린다() {
        assertEquals("serviceKey=***&regionId=31", SensitiveParams.maskQueryString("serviceKey=abc123&regionId=31"));
    }

    @Test
    void 이름_대소문자를_가리지_않는다() {
        assertEquals("SERVICEKEY=***", SensitiveParams.maskQueryString("SERVICEKEY=abc123"));
        assertEquals("appkey=***", SensitiveParams.maskQueryString("appkey=xyz"));
    }

    @Test
    void 민감한_이름_넷을_모두_가린다() {
        assertEquals(
                "serviceKey=***&appKey=***&password=***&token=***",
                SensitiveParams.maskQueryString("serviceKey=a&appKey=b&password=c&token=d"));
    }

    @Test
    void 값이_없는_파라미터도_깨지지_않는다() {
        assertEquals("mood=&regionId=31", SensitiveParams.maskQueryString("mood=&regionId=31"));
    }

    @Test
    void 등호가_없는_조각은_그대로_둔다() {
        assertEquals("flag&regionId=31", SensitiveParams.maskQueryString("flag&regionId=31"));
    }

    @Test
    void null_이면_빈_문자열이다() {
        assertEquals("", SensitiveParams.maskQueryString(null));
    }

    @Test
    void 퍼센트_인코딩된_이름도_가린다() {
        assertEquals("t%6Fken=***", SensitiveParams.maskQueryString("t%6Fken=secret"));
    }

    @Test
    void 이름_앞뒤_공백이_있어도_가린다() {
        assertEquals("+token=***", SensitiveParams.maskQueryString("+token=secret"));
    }

    @Test
    void 깨진_퍼센트_인코딩이어도_예외_없이_통과한다() {
        assertEquals("%zz=secret", SensitiveParams.maskQueryString("%zz=secret"));
    }
}
