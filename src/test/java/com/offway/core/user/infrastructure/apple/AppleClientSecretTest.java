package com.offway.core.user.infrastructure.apple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;
import com.offway.core.user.config.AuthProperties;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Apple client secret 을 <b>Apple 이 받아들일 모양으로</b> 만드는가(#287).
 *
 * <p>이 JWT 는 우리가 그 앱의 주인임을 증명하는 유일한 수단이다. 클레임 하나가 어긋나면 토큰 교환이 통째로
 * 거절되고, 그러면 탈퇴 시 Apple 연결 해제를 영영 못 한다 — 심사 항목이라 그대로 두면 리젝 사유가 된다.
 *
 * <p><b>{@code .p8} 파싱이 특히 미끄럽다.</b> 환경변수에 담긴 것은 PEM 파일 <b>전체를 base64 한 것</b>이라,
 * 한 번 풀면 머리말·개행이 있는 PEM 문자열이 나온다. 그것을 다시 통째로 디코딩하면 깨지는데, 그 실수는
 * 실제 키를 넣어보기 전에는 드러나지 않는다.
 */
class AppleClientSecretTest {

    private static final String TEAM_ID = "AWV8LRP46J";
    private static final String KEY_ID = "3Q22536QKC";
    private static final String BUNDLE_ID = "com.nth.offway";
    private static final String SERVICE_ID = "com.nth.offway.service";

    /** 실제 {@code .p8} 과 같은 모양 — PEM 을 통째로 base64 한 문자열. */
    private static String p8Base64() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        return Base64.getEncoder().encodeToString(pem.getBytes());
    }

    private static AppleClientSecret secret(String teamId, String keyId, String p8) {
        return new AppleClientSecret(
                new AuthProperties.Apple(teamId, keyId, p8), List.of(SERVICE_ID, BUNDLE_ID));
    }

    @Test
    void Apple_이_요구하는_클레임으로_서명한다() throws Exception {
        AppleClientSecret clientSecret = secret(TEAM_ID, KEY_ID, p8Base64());

        SignedJWT jwt = SignedJWT.parse(clientSecret.issue(BUNDLE_ID));

        assertEquals(JWSAlgorithm.ES256, jwt.getHeader().getAlgorithm(), "Apple 은 ES256 만 받는다");
        assertEquals(KEY_ID, jwt.getHeader().getKeyID(), "kid 로 어느 .p8 인지 알린다");
        assertEquals(TEAM_ID, jwt.getJWTClaimsSet().getIssuer(), "iss 는 팀 식별자다");
        assertEquals(BUNDLE_ID, jwt.getJWTClaimsSet().getSubject(), "sub 은 토큰을 발급받은 그 클라이언트다");
        assertEquals(List.of("https://appleid.apple.com"), jwt.getJWTClaimsSet().getAudience());
    }

    @Test
    void 수명이_짧고_이미_만료되어_있지_않다() throws Exception {
        // 길수록 유출됐을 때 남의 앱을 대신할 수 있는 기간이 길어진다. 반대로 0 이면 만들자마자 거절된다.
        SignedJWT jwt = SignedJWT.parse(secret(TEAM_ID, KEY_ID, p8Base64()).issue(BUNDLE_ID));

        Instant issuedAt = jwt.getJWTClaimsSet().getIssueTime().toInstant();
        Instant expiresAt = jwt.getJWTClaimsSet().getExpirationTime().toInstant();
        assertTrue(expiresAt.isAfter(Instant.now()), "만들자마자 만료됐다");
        assertTrue(expiresAt.isBefore(issuedAt.plusSeconds(600)), "수명이 10분을 넘는다");
    }

    @Test
    void 매번_새로_만든다() throws Exception {
        // 캐시하면 만료 경계에서 "방금 만든 것이 이미 만료" 인 창이 생긴다.
        AppleClientSecret clientSecret = secret(TEAM_ID, KEY_ID, p8Base64());

        assertEquals(2, java.util.Set.of(clientSecret.issue(BUNDLE_ID), clientSecret.issue(SERVICE_ID)).size());
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "null, KEY, P8",
                "TEAM, null, P8",
                "TEAM, KEY, null",
                "'', KEY, P8",
                "TEAM, '  ', P8"
            },
            nullValues = "null")
    void 자격이_하나라도_없으면_비활성이다(String teamId, String keyId, String p8) {
        // 부팅을 막지 않는다 — .p8 없이도 뜨고 로그인도 되는 것이 이 레포의 불변식이다.
        assertFalse(secret(teamId, keyId, p8).available());
    }

    @Test
    void 자격이_다_있으면_활성이다() throws Exception {
        assertTrue(secret(TEAM_ID, KEY_ID, p8Base64()).available());
    }

    @Test
    void 클라이언트_식별자가_없으면_비활성이다() throws Exception {
        // 자격이 있어도 sub 에 넣을 값이 없으면 Apple 이 거절한다 — 만들 수 있다고 답하면 안 된다.
        AppleClientSecret clientSecret =
                new AppleClientSecret(new AuthProperties.Apple(TEAM_ID, KEY_ID, p8Base64()), List.of());

        assertFalse(clientSecret.available());
    }

    @Test
    void 키가_깨졌으면_설정_문제로_끊는다() {
        // 재시도가 풀어주지 않는 실패다. 조용히 넘기면 탈퇴가 계속 "연결 해제 없이" 성공한다.
        AppleClientSecret clientSecret = secret(TEAM_ID, KEY_ID, Base64.getEncoder().encodeToString("not a pem".getBytes()));

        assertThrows(IllegalStateException.class, () -> clientSecret.issue(BUNDLE_ID));
    }
}
