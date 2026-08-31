package com.offway.core.user.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.user.domain.AuthProvider;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 백오피스 웹 로그인이 <b>켜졌는지</b>를 무엇으로 판정하는가(#343).
 *
 * <p><b>왜 잠그나</b> — 키만 있고 콜백 주소가 없으면 인가 페이지로 보낼 수는 있지만 돌아온 코드를 교환할
 * 수 없다. 그러면 사람은 카카오 동의까지 다 마치고 <b>마지막에서야</b> 실패를 본다. 시작 전에 끊는 것이
 * 이 판정의 목적이라, 조건이 헐거워지면 그 목적이 조용히 사라진다.
 *
 * <p>앱 로그인 판정과 <b>따로</b>여야 한다는 것도 함께 본다. 콜백 주소는 웹에만 필요한 값이라, 이것이
 * 비었다고 앱 로그인까지 막으면 로컬 실행성 불변식을 깬다.
 */
class AuthPropertiesWebLoginTest {

    private static final String REST_API_KEY = "test-rest-api-key";
    private static final String REDIRECT_URI = "https://api.example.com/api/v1/auth/oauth2/kakao/callback";

    private static AuthProperties properties(String restApiKey, String redirectUri, String clientSecret) {
        return new AuthProperties(
                null,
                Map.of(AuthProvider.KAKAO, new AuthProperties.Oidc(List.of("1234567"), restApiKey, redirectUri, clientSecret)),
                null,
                null);
    }

    @Test
    void 키와_콜백_주소가_둘_다_있으면_웹_로그인을_받는다() {
        assertTrue(properties(REST_API_KEY, REDIRECT_URI, null).kakaoWebLoginConfigured());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void 콜백_주소가_없으면_웹_로그인을_받지_않는다(String redirectUri) {
        assertFalse(properties(REST_API_KEY, redirectUri, null).kakaoWebLoginConfigured());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void 키가_없으면_웹_로그인을_받지_않는다(String restApiKey) {
        assertFalse(properties(restApiKey, REDIRECT_URI, null).kakaoWebLoginConfigured());
    }

    @Test
    void 콜백_주소가_없어도_앱_로그인은_그대로다() {
        // 로컬 실행성 불변식 — 웹 설정이 비었다고 앱 로그인까지 막으면 안 된다.
        assertTrue(properties(REST_API_KEY, null, null).kakaoConfigured());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void client_secret_은_비면_싣지_않는다(String clientSecret) {
        // 콘솔에서 안 켠 앱에 빈 값이라도 보내면 교환이 거절된다.
        assertEquals(Optional.empty(), properties(REST_API_KEY, REDIRECT_URI, clientSecret).kakaoClientSecret());
    }

    @Test
    void client_secret_이_있으면_싣는다() {
        assertEquals(
                Optional.of("secret-value"),
                properties(REST_API_KEY, REDIRECT_URI, "secret-value").kakaoClientSecret());
    }
}
