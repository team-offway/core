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
    void 접두어가_붙은_토큰_이름도_가린다() {
        // \btoken= 만으로는 못 잡는다 — accessToken 은 token 앞이 단어 문자라 경계가 없다(#34).
        // 소셜 로그인부터 이 이름들이 실제로 흐르므로, 놓치면 provider 액세스 토큰이 그대로 로그에 박힌다.
        String message = "401 from POST /auth?accessToken=aaa&idToken=bbb&refreshToken=ccc&client_secret=ddd";

        String masked = SensitiveParams.maskSecretsInText(message);

        assertFalse(masked.contains("aaa"), "실제=" + masked);
        assertFalse(masked.contains("bbb"), "실제=" + masked);
        assertFalse(masked.contains("ccc"), "실제=" + masked);
        assertFalse(masked.contains("ddd"), "실제=" + masked);
    }

    @Test
    void Bearer_토큰을_가린다() {
        // 헤더 형태라 이름=값 규칙으로는 안 걸린다. 이 값은 그대로 카카오 프로필을 부를 수 있는 토큰이다(#34).
        String message = "401 from GET https://kapi.kakao.com/v2/user/me [Authorization: Bearer aBc123.dEf-456_ghi]";

        String masked = SensitiveParams.maskSecretsInText(message);

        assertFalse(masked.contains("aBc123.dEf-456_ghi"), "실제=" + masked);
        assertTrue(masked.contains("Bearer ***"), "실제=" + masked);
        assertTrue(masked.contains("kapi.kakao.com"), "비밀이 아닌 주소는 남아야 한다. 실제=" + masked);
    }

    @Test
    void 토큰_이름을_쿼리에서도_가린다() {
        assertEquals("accessToken=***", SensitiveParams.readableParams("accessToken=abc123"));
        assertEquals("refreshToken=***", SensitiveParams.readableParams("refreshToken=abc123"));
    }

    @Test
    void 디스코드_웹훅_토큰을_가린다() {
        // 이름=값 규칙으로는 안 걸린다 — 토큰이 경로 조각이라 이름이 없다. 그런데 이 URL 끝을 아는 사람은
        // 누구나 우리 채널에 글을 쓸 수 있다(#257).
        String message = "500 from POST https://discord.com/api/webhooks/123456789/aB3-xY_secretTOKEN";

        String masked = SensitiveParams.maskSecretsInText(message);

        assertFalse(masked.contains("aB3-xY_secretTOKEN"), "실제=" + masked);
        assertTrue(masked.contains("/api/webhooks/123456789/***"), "실제=" + masked);
    }

    @Test
    void 자유_텍스트가_비어도_깨지지_않는다() {
        assertEquals("", SensitiveParams.maskSecretsInText(""));
        assertEquals(null, SensitiveParams.maskSecretsInText(null));
    }

    @Test
    void 값_하나에_개행을_넣어_가짜_로그_줄을_만들_수_없다() {
        // 경로 변수(@PathVariable)는 서블릿이 퍼센트 디코딩을 마친 값이라, %0A 가 실제 개행으로 온다.
        String rendered = SensitiveParams.forLog("126508\n2026-01-01 INFO  fake");

        assertFalse(rendered.contains("\n"), "개행이 남으면 로그 줄이 쪼개진다. 실제=" + rendered);
        assertEquals("1265082026-01-01 INFO  fake", rendered);
    }

    @Test
    void 값_하나가_아주_길면_잘라낸다() {
        String rendered = SensitiveParams.forLog("가".repeat(200));

        assertTrue(rendered.endsWith("…"), "잘렸다는 표식이 있어야 한다. 실제 길이=" + rendered.length());
        assertTrue(rendered.length() < 80, "실제 길이=" + rendered.length());
    }

    @Test
    void 값_하나는_다시_디코딩하지_않는다() {
        // 이미 디코딩된 값을 또 풀면 %41 이 A 가 돼, 로그가 실제 요청과 다른 값을 가리킨다.
        assertEquals("126508%41", SensitiveParams.forLog("126508%41"));
    }

    @Test
    void 공개_식별자는_가리지_않는다() {
        // 마스킹 대상은 비밀값이지 콘텐츠 id 가 아니다. 가리면 어느 것이 실패했는지 알 수 없다.
        assertEquals("126508", SensitiveParams.forLog("126508"));
    }

    @Test
    void 값이_null_이어도_깨지지_않는다() {
        assertEquals("", SensitiveParams.forLog(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"access_token", "id_token", "refresh_token", "identity_token"})
    void OAuth_규격_이름의_토큰도_가린다(String name) {
        // 우리 앱은 camelCase 로 보내지만 OAuth 규격(RFC 6749)이 쓰는 이름은 snake_case 다. 제공자 쪽 URL 이
        // 예외 메시지에 실려 들어오는 경로가 있어, 한쪽만 적으면 그 경로로 토큰이 그대로 로그에 남는다.
        assertEquals(name + "=***", SensitiveParams.readableParams(name + "=secret"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"access_token", "id_token", "refresh_token", "identity_token"})
    void 자유_텍스트에_섞인_OAuth_토큰도_가린다(String name) {
        // 두 경로는 서로 다른 규칙으로 가린다 — readableParams 는 이름 목록으로, maskSecretsInText 는
        // 이름=값 패턴으로. 한쪽만 잠그면 다른 쪽에서 이름이 빠져도 테스트가 초록인 채 토큰이 로그에 남는다.
        // 외부 응답이 예외 메시지에 실려 오는 경로가 타는 것은 이쪽이다.
        assertEquals(
                "502 from POST /oauth/token " + name + "=***",
                SensitiveParams.maskSecretsInText("502 from POST /oauth/token " + name + "=secret"));
    }

    @Test
    void 물결이_섞인_Bearer_토큰도_통째로_가린다() {
        // base64url·RFC 6750 이 허용하는 문자다. 문자 집합에서 빠지면 거기서 끊겨 뒷부분이 로그에 남는다.
        // 전체 문자열로 고정한다 — 일부만 보면 "Bearer ***.ghi" 처럼 꼬리가 남아도 통과한다.
        assertEquals(
                "401 from GET /me Authorization: Bearer ***",
                SensitiveParams.maskSecretsInText("401 from GET /me Authorization: Bearer abc~def.ghi"));
    }
}
