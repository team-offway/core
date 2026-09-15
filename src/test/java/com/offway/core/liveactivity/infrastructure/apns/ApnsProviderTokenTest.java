package com.offway.core.liveactivity.infrastructure.apns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * APNs 가 요구하는 provider token 을 만드는 규칙(#575).
 *
 * <p>여기서 잠그는 것 셋. 어느 하나가 어긋나면 발송이 <b>전부</b> 실패한다 — 한 건이 아니라 그날 갱신
 * 전체가 죽는 자리라 단위로 망라한다.
 *
 * <ul>
 *   <li>ES256 으로 서명된다 — {@code .p8} 로 검증이 통과해야 한다
 *   <li>헤더의 {@code kid} 와 {@code iss} 가 설정값이다
 *   <li><b>같은 토큰을 재사용한다</b> — APNs 는 너무 잦은 재발급을 {@code 429} 로 막는다
 * </ul>
 */
class ApnsProviderTokenTest {

    private static final String TEAM_ID = "AWV8LRP46J";

    private static final String KEY_ID = "ABC123DEFG";

    private static final String TOPIC = "com.example.app.push-type.liveactivity";

    @Test
    void p8_로_검증되는_ES256_JWT_를_만든다() throws Exception {
        KeyPair keyPair = ecKeyPair();

        SignedJWT jwt = SignedJWT.parse(new ApnsProviderToken(properties(keyPair)).value());

        assertTrue(jwt.verify(new ECDSAVerifier((ECPublicKey) keyPair.getPublic())), "서명이 검증되지 않는다");
        assertEquals(JWSAlgorithm.ES256, jwt.getHeader().getAlgorithm());
    }

    @Test
    void 헤더의_kid_와_iss_가_설정값이다() throws Exception {
        SignedJWT jwt = SignedJWT.parse(new ApnsProviderToken(properties(ecKeyPair())).value());

        assertEquals(KEY_ID, jwt.getHeader().getKeyID());
        assertEquals(TEAM_ID, jwt.getJWTClaimsSet().getIssuer());
        assertNull(jwt.getJWTClaimsSet().getExpirationTime(),
                "APNs 는 iat 로 만료를 판단한다 — exp 를 넣으면 우리 쪽 수명 관리와 두 개의 진실이 생긴다");
    }

    /**
     * <b>이 클래스의 존재 이유.</b> 로그인 쪽 client secret 은 일부러 매번 새로 만드는데(#287), 여기서
     * 같은 것을 하면 APNs 가 {@code 429 TooManyProviderTokenUpdates} 로 발송을 통째로 막는다.
     */
    @Test
    void 같은_토큰을_재사용한다() {
        ApnsProviderToken providerToken = new ApnsProviderToken(properties(ecKeyPair()));

        assertSame(providerToken.value(), providerToken.value(), "부를 때마다 새로 만들면 APNs 가 429 로 막는다");
    }

    @Test
    void p8_이_깨져_있으면_만들_때_드러난다() {
        ApnsProperties broken = new ApnsProperties(
                TEAM_ID, KEY_ID, Base64.getEncoder().encodeToString("not a pem".getBytes()), TOPIC, false);

        assertThrows(IllegalStateException.class, () -> new ApnsProviderToken(broken));
    }

    /** 운영에서 넘어오는 값과 같은 모양 — {@code .p8}(PEM) 파일 전체를 base64 한 것이다. */
    private static ApnsProperties properties(KeyPair keyPair) {
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        return new ApnsProperties(
                TEAM_ID, KEY_ID, Base64.getEncoder().encodeToString(pem.getBytes()), TOPIC, false);
    }

    private static KeyPair ecKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
