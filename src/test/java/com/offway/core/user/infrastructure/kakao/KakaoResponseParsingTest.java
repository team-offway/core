package com.offway.core.user.infrastructure.kakao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.offway.core.user.domain.UserErrorCode;
import com.offway.core.user.domain.UserException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

/**
 * 카카오 응답을 실제 JSON 모양으로 읽는가(#34).
 *
 * <p><b>통합 테스트가 이 자리를 못 덮는다.</b> 그쪽은 {@link KakaoProfileClient} port 를 stub 으로 갈아끼우므로
 * 파싱 코드가 한 번도 돌지 않는다. 그래서 <b>카카오가 실제로 주는 모양</b>을 여기서 직접 넣는다.
 *
 * <p>특히 {@code id}·{@code app_id} 는 <b>숫자로 온다</b>(문자열이 아니다). 이 강제 변환이 깨지면 {@code app_id}
 * 가 {@code null} 이 되어 <b>카카오 로그인이 전부</b> 502 가 된다 — 일부가 아니라 전부다. 그런데 부팅도 되고 설정도
 * 채워져 있어 원인이 드러나지 않는다.
 */
class KakaoResponseParsingTest {

    /** 우리 앱 번호. 토큰 정보의 {@code app_id} 와 대조하는 값이다. */
    private static final String OUR_APP_ID = "1524138";

    private final KakaoProfileClientImpl client = new KakaoProfileClientImpl(null, new ObjectMapper());

    private static UserErrorCode errorCodeOf(UserException exception) {
        return (UserErrorCode) exception.errorCode();
    }

    @Test
    void 숫자로_오는_app_id_를_문자열로_읽는다() {
        // 카카오 /v1/user/access_token_info 의 실제 모양 — 세 값이 모두 숫자다.
        KakaoTokenInfo info = client.parseTokenInfo("{\"id\":123456789,\"expires_in\":7199,\"app_id\":1524138}");

        assertEquals(OUR_APP_ID, info.appId());
        assertEquals("123456789", info.id());
    }

    @Test
    void 우리_앱_번호와_대조가_성립한다() {
        // 파싱 결과가 실제 검증에 그대로 쓰인다는 것까지 확인한다 — 값만 맞고 타입이 어긋나면 여기서 걸린다.
        KakaoTokenInfo info = client.parseTokenInfo("{\"id\":1,\"app_id\":1524138}");

        assertEquals(true, info.issuedByAnyOf(java.util.List.of(OUR_APP_ID)));
    }

    @Test
    void app_id_가_없으면_502() {
        // 200 인데 발급 앱을 알 수 없는 응답이다. 통과시키면 남의 앱 토큰 검증이 있으나 마나가 된다.
        UserException thrown =
                assertThrows(UserException.class, () -> client.parseTokenInfo("{\"id\":1,\"expires_in\":7199}"));

        assertEquals(UserErrorCode.OIDC_PROVIDER_UNAVAILABLE, errorCodeOf(thrown));
    }

    @Test
    void 숫자로_오는_회원번호를_문자열로_읽는다() {
        KakaoProfile profile = client.parse(
                """
                {"id":123456789,"kakao_account":{"email":"a@b.com","profile":{"nickname":"세빈"}}}""");

        assertEquals("123456789", profile.id());
        assertEquals("세빈", profile.nickname());
        assertEquals("a@b.com", profile.email());
    }

    @Test
    void 동의하지_않은_항목은_null_로_읽는다() {
        // 닉네임·이메일 동의를 안 하면 그 키가 아예 없다. 여기서 터지면 동의 거부 사용자가 로그인을 못 한다.
        KakaoProfile profile = client.parse("{\"id\":123456789,\"kakao_account\":{}}");

        assertEquals("123456789", profile.id());
        assertNull(profile.nickname());
        assertNull(profile.email());
    }

    @Test
    void 회원번호가_없으면_502() {
        UserException thrown = assertThrows(UserException.class, () -> client.parse("{\"kakao_account\":{}}"));

        assertEquals(UserErrorCode.OIDC_PROVIDER_UNAVAILABLE, errorCodeOf(thrown));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not json", "{", "[]"})
    void 비었거나_깨진_응답은_502(String body) {
        assertEquals(
                UserErrorCode.OIDC_PROVIDER_UNAVAILABLE,
                errorCodeOf(assertThrows(UserException.class, () -> client.parseTokenInfo(body))));
        assertEquals(
                UserErrorCode.OIDC_PROVIDER_UNAVAILABLE,
                errorCodeOf(assertThrows(UserException.class, () -> client.parse(body))));
    }
}
