package com.offway.core.liveactivity.infrastructure.apns;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * APNs 가 요구하는 provider authentication token(#575) — {@code .p8} 로 서명한 ES256 JWT.
 *
 * <h2>{@code AppleClientSecret} 과 반대로 캐시한다</h2>
 *
 * <p>로그인 쪽의 client secret 은 <b>일부러 캐시하지 않는다</b>(#287) — 만들자마자 한 번 쓰고 버리므로
 * 캐시가 만료 경계에 창만 만든다. 여기는 정반대다. <b>APNs 는 같은 토큰을 재사용하라고 요구하고</b>,
 * 너무 자주 새로 만들면 {@code 429 TooManyProviderTokenUpdates} 로 발송 전체를 막는다.
 *
 * <p>규격상 재발급 간격은 20분 이상 60분 이하다. 그 사이인 50분마다 갱신한다 — 하한에 붙이면 다시
 * 만드는 횟수가 늘고, 상한에 붙이면 시계 오차만큼 만료된 토큰을 쓰게 된다.
 *
 * <h2>{@code exp} 가 없다</h2>
 *
 * <p>APNs 의 provider token 은 {@code iss}·{@code iat} 와 헤더의 {@code kid} 만 본다. 만료는 APNs 가
 * {@code iat} 로 판단하므로 {@code exp} 를 넣지 않는다 — 넣어도 무시되고, 우리 쪽 수명 관리와 두 개의
 * 진실이 생긴다.
 */
class ApnsProviderToken {

    /**
     * 토큰을 다시 만드는 간격.
     *
     * <p>APNs 규격의 허용 구간(20분~60분) 안쪽이다. 이 값을 20분 아래로 내리면 {@code 429} 가 나고,
     * 60분 위로 올리면 만료된 토큰으로 쏘게 된다 — 양쪽 다 발송이 통째로 실패한다.
     */
    private static final Duration REFRESH_INTERVAL = Duration.ofMinutes(50);

    /** {@code .p8} 의 PEM 머리말·꼬리말 — base64 본문만 남기려고 걷어낸다. */
    private static final String PEM_HEADER = "-----BEGIN PRIVATE KEY-----";

    private static final String PEM_FOOTER = "-----END PRIVATE KEY-----";

    private final ApnsProperties properties;

    /** 서명키는 한 번만 만든다 — PKCS8 파싱은 값싸지 않고 값이 바뀌지 않는다. */
    private final JWSSigner signer;

    private volatile Issued issued;

    ApnsProviderToken(ApnsProperties properties) {
        this.properties = properties;
        this.signer = signer(properties.privateKeyBase64());
    }

    /**
     * 지금 쓸 토큰.
     *
     * <p><b>여러 스레드가 동시에 부른다</b>(발송이 팬아웃이다). 갱신만 잠그고 읽기는 잠그지 않는다 —
     * 만료 직전에 두 스레드가 함께 들어와도 안쪽에서 한 번 더 확인하므로 두 번 만들지 않는다.
     */
    String value() {
        Instant now = Instant.now();
        Issued current = issued;
        if (current != null && current.stillFresh(now)) {
            return current.token();
        }
        synchronized (this) {
            if (issued == null || !issued.stillFresh(now)) {
                issued = new Issued(sign(now), now);
            }
            return issued.token();
        }
    }

    /**
     * APNs 가 <b>이 토큰은 만료됐다</b>({@code 403 ExpiredProviderToken})고 답했을 때 버린다.
     *
     * <p><b>수명만으로는 못 막는다.</b> 우리 쪽 갱신 간격(50분)은 APNs 의 판단과 별개라, 그쪽이 먼저
     * 만료로 보면 캐시가 자연히 늙을 때까지 <b>최대 50분 동안 모든 발송이 실패한다</b> — 자정에 한 번
     * 도는 배치라 그 회차가 통째로 날아간다.
     *
     * <p><b>내가 쓰던 그 토큰일 때만 버린다.</b> 팬아웃이라 여러 스레드가 동시에 같은 거절을 받는데,
     * 무조건 비우면 방금 다른 스레드가 새로 만든 토큰까지 버리고 그 스레드도 다시 만든다 — 재발급이
     * 연쇄하면 APNs 가 {@code 429 TooManyProviderTokenUpdates} 로 막는다.
     *
     * @param stale 거절당한 토큰 문자열
     */
    synchronized void invalidate(String stale) {
        if (issued != null && issued.token().equals(stale)) {
            issued = null;
        }
    }

    private String sign(Instant now) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(properties.teamId())
                .issueTime(Date.from(now))
                .build();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(properties.keyId())
                .build();
        try {
            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (Exception exception) {
            // 설정이 깨진 경우다. 요청마다 같은 결과라 재시도가 풀어주지 않는다 — 호출자가 degrade 를 판단한다.
            throw new IllegalStateException("APNs provider token 을 만들지 못했습니다 — .p8 설정을 확인하세요", exception);
        }
    }

    /**
     * {@code .p8} 본문으로 ES256 서명자를 만든다.
     *
     * <p>환경변수에 담긴 것은 <b>PEM 파일 전체를 base64 한 것</b>이라, 한 번 풀면 머리말·꼬리말·개행이
     * 있는 PEM 문자열이 나온다. 그것을 그대로 다시 base64 디코딩하면 깨진다.
     *
     * <p><b>바깥 base64 는 개행을 관용하는 디코더로 푼다.</b> 이 값을 만드는 흔한 방법
     * ({@code base64 < AuthKey_XXX.p8})이 76자마다 개행을 넣는데, strict 디코더는 그 개행 하나에
     * 예외를 던진다. {@code strip()} 은 앞뒤만 걷어내므로 가운데 개행은 남는다. 그리고 그 예외는
     * {@code providerToken(...)} 이 삼켜 발송이 통째로 {@code DISABLED} 가 된다 — <b>키를 제대로
     * 넣었는데 아무것도 안 나가는</b>, 원인을 짐작하기 가장 어려운 모양이다.
     */
    private static JWSSigner signer(String privateKeyBase64) {
        try {
            String pem = new String(Base64.getMimeDecoder().decode(privateKeyBase64.strip()));
            String body = pem.replace(PEM_HEADER, "").replace(PEM_FOOTER, "").replaceAll("\\s", "");
            ECPrivateKey key = (ECPrivateKey) KeyFactory.getInstance("EC")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
            return new ECDSASigner(key);
        } catch (Exception exception) {
            throw new IllegalStateException("APNs .p8 을 읽지 못했습니다", exception);
        }
    }

    /** 발급된 토큰과 그 시각 — 둘을 따로 두면 갱신 중에 짝이 어긋난다. */
    private record Issued(String token, Instant issuedAt) {

        boolean stillFresh(Instant now) {
            return issuedAt.plus(REFRESH_INTERVAL).isAfter(now);
        }
    }
}
