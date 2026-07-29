package com.offway.core.user.service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.offway.core.user.config.AuthProperties;
import com.offway.core.user.domain.UserException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * 자체 토큰 발급·검증. access 는 HS256 서명 JWT(무상태), refresh 는 난수 문자열이고 DB 에는 해시만 남는다.
 *
 * <p>서명키는 최소 32바이트여야 한다(HS256). local 은 개발용 고정값을 쓰고 prod 는 환경변수로 주입한다 — 키 없이 뜨면
 * 아무 토큰이나 위조 가능해지므로 부팅 단계에서 막는다.
 */
@Component
public class TokenIssuer {

    /** 자체 토큰의 issuer — provider 토큰과 섞이지 않게 구분한다. */
    private static final String ISSUER = "offway";

    /** HS256 최소 키 길이(바이트). */
    private static final int MIN_SECRET_BYTES = 32;

    /** refresh 토큰 난수 길이(바이트). */
    private static final int REFRESH_TOKEN_BYTES = 32;

    private static final String HASH_ALGORITHM = "SHA-256";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenIssuer(AuthProperties authProperties) {
        SecretKey key = secretKey(authProperties.jwt().secret());
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        this.accessTtl = authProperties.jwt().accessTtl();
        this.refreshTtl = authProperties.jwt().refreshTtl();
    }

    /** 사용자 식별자를 subject 로 하는 access 토큰. */
    public String issueAccessToken(UUID userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(accessTtl))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    /**
     * access 토큰에서 사용자 식별자를 꺼낸다. 서명·만료·형식 중 하나라도 어긋나면 {@code USER-004}.
     *
     * <p>구체 사유는 응답에 담지 않는다 — 공격자에게 어디까지 맞췄는지 알려줄 이유가 없다.
     */
    public UUID parseAccessToken(String accessToken) {
        try {
            Jwt jwt = decoder.decode(accessToken);
            return UUID.fromString(jwt.getSubject());
        } catch (JwtException | IllegalArgumentException | NullPointerException exception) {
            throw UserException.invalidAccessToken();
        }
    }

    /** refresh 토큰 원문. 클라이언트에만 나가고 서버는 해시만 보관한다. */
    public String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** refresh 토큰 원문 → 저장·조회용 SHA-256 hex(64자). */
    public String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 을 사용할 수 없습니다.", exception);
        }
    }

    public Instant refreshTokenExpiry(Instant from) {
        return from.plus(refreshTtl);
    }

    public long accessTokenSeconds() {
        return accessTtl.toSeconds();
    }

    private static SecretKey secretKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "offway.auth.jwt.secret 이 비어 있습니다. prod 는 환경변수로 주입해야 합니다(local 은 기본값 제공).");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "offway.auth.jwt.secret 은 최소 " + MIN_SECRET_BYTES + "바이트여야 합니다: " + bytes.length);
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
