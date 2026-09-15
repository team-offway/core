package com.offway.core.liveactivity.infrastructure.apns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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

    /**
     * <b>운영에서 값을 만드는 흔한 방법이 개행을 넣는다.</b>
     *
     * <p>{@code base64 < AuthKey_XXX.p8} 은 GNU coreutils·macOS 기본 설정에서 76자마다 개행을 넣는데,
     * strict 디코더는 그 개행 하나에 예외를 던진다. 그 예외는 {@code providerToken(...)} 이 삼켜
     * 발송이 통째로 {@code DISABLED} 가 된다 — <b>키를 제대로 넣었는데 아무것도 안 나가는</b> 모양이라
     * 원인을 짐작하기 가장 어렵다(#577 리뷰).
     */
    @Test
    void 바깥_base64_에_개행이_섞여도_읽는다() throws Exception {
        KeyPair keyPair = ecKeyPair();

        SignedJWT jwt = SignedJWT.parse(new ApnsProviderToken(wrappedProperties(keyPair)).value());

        assertTrue(jwt.verify(new ECDSAVerifier((ECPublicKey) keyPair.getPublic())),
                "76자마다 개행이 들어간 base64 를 못 읽었다 — 운영에서 발송이 통째로 비활성된다");
    }

    /**
     * APNs 가 만료를 알렸을 때 <b>그 토큰을 쓰던 경우에만</b> 버린다.
     *
     * <p>팬아웃이라 여러 스레드가 동시에 같은 거절을 받는다. 무조건 비우면 방금 다른 스레드가 새로 만든
     * 토큰까지 버리고 그 스레드도 다시 만드는데, 그 재발급 연쇄가 {@code 429} 를 부른다.
     */
    @Test
    void 만료를_알린_그_토큰일_때만_버린다() {
        ApnsProviderToken providerToken = new ApnsProviderToken(properties(ecKeyPair()));
        String current = providerToken.value();

        providerToken.invalidate("남이 쓰던 낡은 토큰");

        assertSame(current, providerToken.value(), "내 것이 아닌 거절로 새 토큰을 버렸다");
    }

    @Test
    void 만료를_알리면_새로_만든다() {
        ApnsProviderToken providerToken = new ApnsProviderToken(properties(ecKeyPair()));
        String expired = providerToken.value();

        providerToken.invalidate(expired);

        // 같은 초에 다시 만들면 클레임이 같아 문자열이 겹칠 수 있다. 캐시를 실제로 버렸는지는
        // "같은 인스턴스를 돌려주는가" 로 본다 — 안 버렸으면 붙잡아 둔 그 String 이 그대로 나온다.
        assertNotSame(expired, providerToken.value(), "만료된 토큰을 그대로 다시 쓴다 — 최대 50분간 전부 실패한다");
    }

    @Test
    void p8_이_깨져_있으면_만들_때_드러난다() {
        ApnsProperties broken = new ApnsProperties(
                TEAM_ID, KEY_ID, Base64.getEncoder().encodeToString("not a pem".getBytes()), TOPIC, false);

        assertThrows(IllegalStateException.class, () -> new ApnsProviderToken(broken));
    }

    /** 운영에서 넘어오는 값과 같은 모양 — {@code .p8}(PEM) 파일 전체를 base64 한 것이다. */
    private static ApnsProperties properties(KeyPair keyPair) {
        return new ApnsProperties(
                TEAM_ID, KEY_ID, Base64.getEncoder().encodeToString(pem(keyPair).getBytes()), TOPIC, false);
    }

    /** {@code base64 < AuthKey_XXX.p8} 이 만드는 모양 — 76자마다 개행이 들어간다. */
    private static ApnsProperties wrappedProperties(KeyPair keyPair) {
        return new ApnsProperties(
                TEAM_ID, KEY_ID, Base64.getMimeEncoder().encodeToString(pem(keyPair).getBytes()), TOPIC, false);
    }

    private static String pem(KeyPair keyPair) {
        return "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
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
