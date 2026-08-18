package com.offway.core.user.infrastructure.oidc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

/**
 * ID 토큰 검증(#34) — <b>남의 앱 토큰으로 로그인되지 않는가</b>.
 *
 * <p>이 셋이 뚫리면 계정 탈취가 된다. {@code aud} 를 안 보면 다른 앱에서 발급된 토큰을 그대로 받아주고,
 * {@code iss} 를 안 보면 아무나 서명한 토큰이 통하며, 만료를 안 보면 유출된 옛 토큰이 영원히 산다.
 *
 * <p><b>검증이 있다는 것만으로는 부족하다.</b> 지우거나 느슨하게 바꿔도 아무 테스트가 깨지지 않으면 그 검증은
 * 다음 리팩터링에서 조용히 사라진다. 여기서 거절되는 것을 직접 확인한다.
 *
 * <p>서명 자체는 {@code NimbusJwtDecoder} 가 JWKS 로 확인하므로 이 테스트의 대상이 아니다 — 그건 라이브러리가
 * 보장하고, 우리가 얹은 것은 issuer·audience 다. 만료는 기본 검증에 포함돼 함께 확인한다.
 */
class OidcTokenValidationTest {

    private static final String OUR_APP = "our-client-id.apps.googleusercontent.com";
    private static final String OTHER_APP = "someone-else.apps.googleusercontent.com";

    /** Google 이 실제로 쓰는 두 표기. 하나만 허용하면 다른 표기를 받은 사용자가 전부 401 이 된다. */
    private static final List<String> ISSUERS = List.of("https://accounts.google.com", "accounts.google.com");

    private static boolean accepts(Jwt token) {
        return !NimbusOidcVerifier.tokenValidator(ISSUERS, List.of(OUR_APP))
                .validate(token)
                .hasErrors();
    }

    private static Jwt.Builder token() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("ignored")
                .header("alg", "RS256")
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .claim(JwtClaimNames.ISS, "https://accounts.google.com")
                .audience(List.of(OUR_APP))
                .subject("provider-user-id");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://accounts.google.com", "accounts.google.com"})
    void 우리가_아는_iss_표기는_받는다(String issuer) {
        assertTrue(accepts(token().claim(JwtClaimNames.ISS, issuer).build()));
    }

    @Test
    void 모르는_iss_는_거절한다() {
        assertFalse(accepts(token().claim(JwtClaimNames.ISS, "https://evil.example.com").build()));
    }

    @Test
    void iss_가_없으면_거절한다() {
        // 클레임을 통째로 비워 오는 토큰도 있다. 없는 것을 "일치" 로 흘리면 검증이 없는 것과 같다.
        assertFalse(accepts(token().claims(claims -> claims.remove(JwtClaimNames.ISS)).build()));
    }

    @Test
    void 남의_앱_토큰은_거절한다() {
        // 이 검증이 없으면 공격자가 자기 앱에서 피해자 토큰을 받아 그대로 우리 서버에 던질 수 있다.
        assertFalse(accepts(token().audience(List.of(OTHER_APP)).build()));
    }

    @Test
    void 우리_앱이_섞여_있으면_받는다() {
        // aud 는 복수로 올 수 있다. 하나라도 우리 것이면 우리 앱을 위해 발급된 토큰이다.
        assertTrue(accepts(token().audience(List.of(OTHER_APP, OUR_APP)).build()));
    }

    @Test
    void aud_가_없으면_거절한다() {
        assertFalse(accepts(token().claims(claims -> claims.remove(JwtClaimNames.AUD)).build()));
    }

    @Test
    void 만료된_토큰은_거절한다() {
        // 유출된 옛 토큰이 영원히 사는 것을 막는다. 기본 검증에 포함돼 있지만 그것 역시 지워질 수 있다.
        Instant past = Instant.now().minus(2, ChronoUnit.HOURS);

        assertFalse(accepts(token().issuedAt(past).expiresAt(past.plus(1, ChronoUnit.HOURS)).build()));
    }

    @Test
    void audience가_비면_모든_토큰을_거절한다() {
        // 설정이 빠진 채로 뜨면 aud 검증이 무력해진다 — 그때는 아무도 통과하지 못하는 쪽이 안전하다.
        Jwt valid = token().build();

        assertTrue(NimbusOidcVerifier.tokenValidator(ISSUERS, List.of())
                .validate(valid)
                .hasErrors());
    }

    @Test
    void 정상_토큰은_통과한다() {
        // 위 거절들이 "전부 거절" 이라서 통과하는 것이 아님을 보인다.
        assertTrue(accepts(token().build()));
    }
}
