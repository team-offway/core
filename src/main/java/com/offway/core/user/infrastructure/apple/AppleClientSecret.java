package com.offway.core.user.infrastructure.apple;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.offway.core.user.config.AuthProperties;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Apple 토큰 API 가 요구하는 client secret 을 만든다(#287).
 *
 * <p><b>문자열이 아니라 JWT 다.</b> 다른 provider 는 발급받은 secret 문자열을 그대로 보내지만, Apple 은
 * {@code .p8} 개인키로 서명한 ES256 JWT 를 받는다. 그래서 "secret 을 설정에서 읽어 넘긴다" 가 성립하지 않고
 * 이 클래스가 필요하다.
 *
 * <p><b>매번 새로 만든다.</b> 수명을 짧게(5분) 두고 캐시하지 않는다 — 서명 한 번은 마이크로초 단위라 아낄
 * 값이 아니고, 캐시하면 만료 경계에서 "방금 만든 것이 이미 만료" 인 창이 생긴다. Apple 은 최대 6개월까지
 * 허용하지만 길수록 유출됐을 때 남의 앱을 대신할 수 있는 기간이 길어진다.
 *
 * <p><b>키가 없으면 만들지 않는다.</b> 로컬·CI 는 {@code .p8} 없이 뜨는 것이 이 레포의 불변식이라, 여기서
 * 예외를 던지면 그 환경의 탈퇴가 통째로 막힌다. 호출자가 {@link #available()} 로 먼저 묻는다.
 */
@Slf4j
class AppleClientSecret {

    /** Apple 이 client secret 의 {@code aud} 로 요구하는 값 — 고정이다. */
    private static final String APPLE_AUDIENCE = "https://appleid.apple.com";

    /**
     * client secret 수명.
     *
     * <p>Apple 상한은 6개월이다. 우리는 만들자마자 한 번 쓰고 버리므로 그 길이가 필요 없다 — 시계 오차를
     * 흡수할 만큼만 둔다.
     */
    private static final Duration LIFETIME = Duration.ofMinutes(5);

    /** {@code .p8} 의 PEM 머리말·꼬리말 — base64 본문만 남기려고 걷어낸다. */
    private static final String PEM_HEADER = "-----BEGIN PRIVATE KEY-----";

    private static final String PEM_FOOTER = "-----END PRIVATE KEY-----";

    private final AuthProperties.Apple config;

    /** {@code sub}·{@code aud} 로 쓸 우리 클라이언트 식별자들 — Apple audience 설정과 같은 값이다. */
    private final List<String> clientIds;

    AppleClientSecret(AuthProperties.Apple config, List<String> clientIds) {
        this.config = config;
        this.clientIds = List.copyOf(clientIds);
    }

    /** 만들 수 있는가 — 자격 셋과 클라이언트 식별자가 다 있어야 한다. */
    boolean available() {
        return config.configured() && !clientIds.isEmpty();
    }

    /**
     * 이 클라이언트로 서명한 client secret.
     *
     * <p>{@code sub} 는 <b>토큰을 발급받은 그 클라이언트</b>여야 한다. 네이티브 로그인은 Bundle ID, 웹은
     * Service ID 라 값이 갈리는데, 어느 쪽인지는 이 클래스가 알 수 없어 호출자가 정한다.
     */
    String issue(String clientId) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(config.teamId())
                .subject(clientId)
                .audience(APPLE_AUDIENCE)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(LIFETIME)))
                .build();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(config.keyId())
                .type(JOSEObjectType.JWT)
                .build();
        try {
            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(signer());
            return jwt.serialize();
        } catch (Exception exception) {
            // 키 형식이 깨졌거나 base64 가 잘린 경우다. 설정 문제라 요청마다 같은 결과이므로
            // 여기서 예외를 올려도 재시도가 풀어주지 않는다 — 호출자가 degrade 를 판단한다.
            throw new IllegalStateException("Apple client secret 을 만들지 못했습니다 — .p8 설정을 확인하세요", exception);
        }
    }

    /** 우리 클라이언트 식별자 후보 — 저장된 것이 없을 때 순서대로 시도하는 데 쓴다. */
    List<String> clientIds() {
        return clientIds;
    }

    private JWSSigner signer() throws Exception {
        byte[] pkcs8 = Base64.getDecoder().decode(pemBody());
        ECPrivateKey key = (ECPrivateKey)
                KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        return new ECDSASigner(key);
    }

    /**
     * {@code .p8} 본문만 남긴다.
     *
     * <p>환경변수에 담긴 것은 <b>PEM 파일 전체를 base64 한 것</b>이라, 한 번 풀면 머리말·꼬리말·개행이 있는
     * PEM 문자열이 나온다. 그것을 그대로 다시 base64 디코딩하면 깨진다.
     */
    private String pemBody() {
        String pem = new String(Base64.getDecoder().decode(config.privateKeyBase64().strip()));
        return pem.replace(PEM_HEADER, "")
                .replace(PEM_FOOTER, "")
                .replaceAll("\\s", "");
    }
}
