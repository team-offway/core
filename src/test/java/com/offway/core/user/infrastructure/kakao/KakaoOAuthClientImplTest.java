package com.offway.core.user.infrastructure.kakao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.offway.core.user.config.AuthProperties;
import com.offway.core.user.domain.UserException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

/**
 * 카카오 토큰 교환 응답 파싱(#343).
 *
 * <p>통합 테스트는 이 클래스를 감싸는 port 를 stub 으로 갈아끼우므로 파싱 코드가 한 번도 돌지 않는다.
 * 카카오가 실제로 주는 JSON 모양을 직접 넣어 확인하려면 여기까지 내려와야 한다.
 *
 * <p><b>왜 잠그나</b> — 토큰이 없는 200 을 그냥 넘기면 {@code null} 을 들고 프로필 조회로 내려가, 한 단계
 * 뒤에서 엉뚱한 이유로 실패한다. 로그가 "프로필 조회 실패" 를 가리켜 원인을 찾을 때 잘못된 엔드포인트를
 * 들여다보게 된다.
 */
class KakaoOAuthClientImplTest {

    private static KakaoOAuthClientImpl client() {
        return new KakaoOAuthClientImpl(
                WebClient.builder().build(), new ObjectMapper(), new AuthProperties(null, null, null, null));
    }

    @Test
    void 액세스_토큰을_꺼낸다() {
        String body =
                """
                {"token_type":"bearer","access_token":"aaa.bbb.ccc","expires_in":21599,
                 "refresh_token":"rrr","refresh_token_expires_in":5183999}
                """;

        assertEquals("aaa.bbb.ccc", client().parse(body));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 응답이_비면_외부_실패로_본다(String body) {
        assertThrows(UserException.class, () -> client().parse(body));
    }

    @Test
    void 토큰이_없는_200_은_성공으로_넘기지_않는다() {
        // resultCode 는 성공인데 결과가 비어 오는 경우와 같은 자리다 — 예외보다 위험한 조용한 실패다.
        assertThrows(UserException.class, () -> client().parse("{\"token_type\":\"bearer\"}"));
    }

    @Test
    void 토큰이_빈_문자열이어도_거절한다() {
        assertThrows(UserException.class, () -> client().parse("{\"access_token\":\"\"}"));
    }

    @Test
    void 깨진_JSON_은_외부_실패로_본다() {
        assertThrows(UserException.class, () -> client().parse("{\"access_token\""));
    }
}
