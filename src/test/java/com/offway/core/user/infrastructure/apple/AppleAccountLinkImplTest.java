package com.offway.core.user.infrastructure.apple;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.user.config.AuthProperties;
import com.offway.core.user.domain.AuthProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 이 adapter 가 선언한 <b>"어느 쪽도 예외를 올리지 않는다"</b> 를 지키는가(#287).
 *
 * <p>교환은 로그인 경로에, 해제는 탈퇴 경로에 붙는다. 여기서 예외가 새면 <b>Apple 연결이 남는 것</b>을 피하려다
 * <b>로그인·탈퇴 자체를 막는</b> 결과가 되는데, 그건 훨씬 나쁜 교환이다.
 *
 * <p><b>회귀 하나를 잡아 둔다.</b> 예전에는 {@code try} 가 HTTP 호출만 감싸서, 그 앞에 있던 client secret 서명이
 * 계약 밖에 있었다. {@code .p8} 형식이 깨져 있으면 {@code configured()} 는 값이 있다는 이유로 true 를 돌려주고
 * ({@code isBlank} 만 본다) — 로그인마다 서명이 터져 <b>Apple 로그인 전체가 500</b> 이 됐다.
 *
 * <p>그 경로는 기존 테스트로는 안 잡혔다. 통합 테스트는 {@link StubAppleAccountLink} 로 이 adapter 를 통째로
 * 갈아 끼우고, {@link AppleClientSecretTest} 는 {@code issue()} 가 <b>던지는 것</b>만 확인한다 — 그 예외를
 * adapter 가 <b>삼키는지</b>를 보는 자리가 없었다.
 */
class AppleAccountLinkImplTest {

    private static final String BUNDLE_ID = "com.nth.offway";

    private static final String AUTHORIZATION_CODE = "c-any";

    private static final String REFRESH_TOKEN = "r-any";

    /**
     * <b>값은 있는데 {@code .p8} 이 아니다.</b> 환경변수에 오타가 나거나 base64 를 한 번 덜 감싼 흔한 모양이고,
     * 설정 검사({@code configured()})는 공백만 보므로 이걸 통과시킨다.
     */
    private static AppleAccountLinkImpl withBrokenKey() {
        return link("이건-base64-도-PEM-도-아니다");
    }

    private static AppleAccountLinkImpl link(String privateKeyBase64) {
        AuthProperties properties = new AuthProperties(
                null,
                Map.of(AuthProvider.APPLE, new AuthProperties.Oidc(List.of(BUNDLE_ID), null, null, null)),
                new AuthProperties.Apple("TEAM123456", "KEY1234567", privateKeyBase64),
                null);
        return new AppleAccountLinkImpl(WebClient.builder().build(), properties);
    }

    @Test
    void 키가_깨져_있어도_교환은_예외를_올리지_않는다() {
        // 여기서 던지면 로그인 응답이 500 이 된다 — Apple 을 쓰는 사용자 전원이 못 들어온다.
        assertTrue(withBrokenKey().exchange(AUTHORIZATION_CODE, BUNDLE_ID).isEmpty());
    }

    @Test
    void 키가_깨져_있어도_해제는_예외를_올리지_않는다() {
        // 여기서 던지면 탈퇴가 실패한다 — 계정을 지울 권리가 외부 설정 상태에 묶인다.
        assertFalse(withBrokenKey().revoke(REFRESH_TOKEN, BUNDLE_ID));
    }

    @Test
    void 자격이_아예_없으면_외부를_부르지도_않는다() {
        // available() 이 false 라 네트워크가 없는 이 테스트에서도 즉시 돌아온다.
        AppleAccountLinkImpl unconfigured = link(null);

        assertTrue(unconfigured.exchange(AUTHORIZATION_CODE, BUNDLE_ID).isEmpty());
        assertFalse(unconfigured.revoke(REFRESH_TOKEN, BUNDLE_ID));
    }

    @Test
    void 교환할_코드가_없으면_건너뛴다() {
        assertTrue(withBrokenKey().exchange("  ", BUNDLE_ID).isEmpty());
    }

    @Test
    void 끊을_토큰이_없으면_건너뛴다() {
        assertFalse(withBrokenKey().revoke(null, BUNDLE_ID));
    }
}
