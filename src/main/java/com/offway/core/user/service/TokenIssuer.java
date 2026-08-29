package com.offway.core.user.service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.offway.core.user.config.AuthProperties;
import com.offway.core.user.domain.AccountRole;
import com.offway.core.user.domain.UserException;
import com.offway.core.user.service.dto.AuthenticatedAccount;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
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

    /**
     * 역할 클레임 이름(#342).
     *
     * <p><b>옛 토큰에는 이 클레임이 없다.</b> 배포 시점에 이미 나가 있는 access 토큰이 만료(1시간)될 때까지
     * 그렇다. 없으면 {@link AccountRole#USER} 하나로 읽어, 그 사이 사용자가 갑자기 권한을 잃지 않게 한다.
     */
    private static final String ROLES_CLAIM = "roles";

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

    /**
     * 사용자 식별자를 subject 로 하는 access 토큰. 역할을 함께 싣는다(#342).
     *
     * <p><b>역할을 인자로 받는 이유</b> — 편의 오버로드를 두면 호출부가 그쪽을 쓰다가 어드민에게 권한 없는
     * 토큰을 발급한다. 부르는 쪽이 매번 "이 사람은 무엇인가" 를 답하게 한다.
     *
     * <p>토큰에 박아 두므로 <b>화이트리스트에서 빼도 최대 access TTL 만큼은 어드민으로 남는다.</b> 요청마다
     * DB 를 보면 즉시 끊기지만 모든 요청이 조회를 하나 더 하게 된다 — 어드민이 소수이고 되돌릴 일이 드물어
     * 무상태 쪽을 골랐다. 즉시 끊어야 할 일이 생기면 서명키를 돌리는 것이 확실하다.
     */
    public String issueAccessToken(UUID userId, Set<AccountRole> roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(accessTtl))
                .claim(ROLES_CLAIM, roles.stream().map(AccountRole::name).toList())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    /**
     * access 토큰에서 사용자 식별자를 꺼낸다. 서명·만료·형식 중 하나라도 어긋나면 {@code USER-004}.
     *
     * <p>구체 사유는 응답에 담지 않는다 — 공격자에게 어디까지 맞췄는지 알려줄 이유가 없다.
     */
    public AuthenticatedAccount parseAccessToken(String accessToken) {
        try {
            Jwt jwt = decoder.decode(accessToken);
            return new AuthenticatedAccount(UUID.fromString(jwt.getSubject()), rolesOf(jwt));
        } catch (JwtException | IllegalArgumentException | NullPointerException exception) {
            // 원인을 달아 던진다 — 여기서 로그를 찍지 않는 이유는, 이 시점엔 어느 요청인지·누구인지를
            // 모르기 때문이다. 그건 필터가 알고, 필터가 한 줄로 합쳐 남긴다(#41).
            throw UserException.invalidAccessToken(exception);
        }
    }

    /**
     * 토큰이 실은 역할. 클레임이 없거나 비면 {@link AccountRole#USER} 하나로 읽는다.
     *
     * <p>비었을 때 <b>권한 없음</b>이 아니라 USER 로 떨어뜨리는 이유는, 이 클레임이 생기기 전에 발급된
     * 토큰이 만료 전까지 살아 있기 때문이다. 배포 직후 한 시간 동안 모두가 쓰기를 못 하게 할 수는 없다.
     */
    private static Set<AccountRole> rolesOf(Jwt jwt) {
        List<String> names = jwt.getClaimAsStringList(ROLES_CLAIM);
        Set<AccountRole> roles = AccountRole.parse(names);
        return roles.isEmpty() ? Set.of(AccountRole.USER) : roles;
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
