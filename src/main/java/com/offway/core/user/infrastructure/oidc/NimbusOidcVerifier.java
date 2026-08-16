package com.offway.core.user.infrastructure.oidc;

import com.offway.core.user.config.AuthProperties;
import com.offway.core.common.logging.RootCause;
import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.SocialIdentity;
import com.offway.core.user.domain.UserException;
import com.offway.core.user.infrastructure.social.SocialIdentityVerifier;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestOperations;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * 서명된 ID 토큰(Apple · Google)을 provider 공개키(JWKS)로 검증하는 어댑터.
 *
 * <p><b>요청 경로에 외부 호출이 없다.</b> provider 별 {@link JwtDecoder} 를 만들어 캐시하고, 디코더가 JWKS 를 내부
 * 캐시하므로 매 로그인마다 provider 를 부르지 않는다. 키가 회전됐을 때만(kid 불일치) 다시 가져온다.
 *
 * <p>새 의존성이 없다 — 이미 있던 {@code spring-security-oauth2-jose}(Nimbus)가 JWKS 검증을 제공한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NimbusOidcVerifier implements SocialIdentityVerifier {

    /** JWKS 조회 상한 — 로그인 경로라 오래 물릴 수 없다. 기본값(30초)을 그대로 두지 않는 이유는 jwksClient() 주석에. */
    private static final Duration JWKS_TIMEOUT = Duration.ofSeconds(3);

    /**
     * 공개키 캐시 수명.
     *
     * <p>10분이다. 백그라운드 갱신은 한도 없는 정적 문서라 사실상 공짜인데(provider 당 5분 주기여도 하루
     * 수백 회), 수명을 늘리면 <b>회전된 키를 모르는 채로 있는 창</b>이 그만큼 길어진다. 그 창에서 정상
     * 토큰이 강제 갱신을 유발하고 rate limit 에 걸려 502 를 받는다. 공짜인 쪽을 아끼고 비싼 쪽을 늘릴 이유가 없다.
     */
    private static final Duration JWKS_CACHE_TTL = Duration.ofMinutes(10);

    /** 캐시가 비었을 때 다른 스레드가 적재를 기다릴 상한. 호출 상한(3초)보다 길 이유가 없다. */
    private static final Duration JWKS_CACHE_REFRESH_TIMEOUT = JWKS_TIMEOUT;

    /** 만료 이 시간 전부터 미리 받아 둔다 — 만료 직후 요청이 조회를 뒤집어쓰지 않게. */
    private static final Duration JWKS_REFRESH_AHEAD = Duration.ofMinutes(5);

    /**
     * 강제 갱신 사이 최소 간격 — 위조 토큰이 조회를 유발해도 이 간격을 넘지 못한다.
     *
     * <p>10초다. 이 값이 곧 <b>키 회전 직후 정상 토큰이 거절될 수 있는 최악의 시간</b>이다(실측으로 확인했다).
     * 30초로 두면 증폭이 분당 4회, 10초면 12회인데 — 요청당 1회였던 것에서 이미 두 자릿수 배 줄어든 뒤라
     * 그 차이는 무의미하다. 반면 지연은 사용자가 그대로 겪는다. 이득이 포화한 쪽을 더 조이지 않는다.
     */
    private static final Duration JWKS_MIN_REFRESH_INTERVAL = Duration.ofSeconds(10);


    private static final String AUDIENCE_MISMATCH = "audience 가 일치하지 않습니다.";

    private static final String ISSUER_MISMATCH = "issuer 가 일치하지 않습니다.";

    private final AuthProperties authProperties;
    private final Map<AuthProvider, JwtDecoder> decoders = new ConcurrentHashMap<>();

    /** 서명된 ID 토큰을 주는 provider 전부를 맡는다 — 검증 절차가 완전히 같다. */
    @Override
    public boolean supports(AuthProvider provider) {
        return provider.oidc().isPresent();
    }

    @Override
    public SocialIdentity verify(AuthProvider provider, String credential) {
        AuthProvider.Oidc oidc =
                provider.oidc().orElseThrow(() -> new IllegalStateException("서명 검증 대상이 아닌 provider: " + provider));
        List<String> audiences = authProperties.audiencesOf(provider);
        // 설정이 비면 audience 검증이 무력화된다 — 남의 앱 토큰을 받아주느니 그 provider 를 닫는다.
        if (audiences.isEmpty()) {
            log.info("audience 가 설정되지 않은 provider 로그인 시도 provider={}", provider);
            throw UserException.unsupportedProvider();
        }
        Jwt jwt = decode(provider, oidc, credential, audiences);
        return new SocialIdentity(provider, jwt.getSubject(), nicknameOf(oidc, jwt), emailOf(jwt));
    }

    private Jwt decode(AuthProvider provider, AuthProvider.Oidc oidc, String idToken, List<String> audiences) {
        try {
            return decoders
                    .computeIfAbsent(provider, key -> buildDecoder(oidc, audiences))
                    .decode(idToken);
        } catch (BadJwtException exception) {
            // 서명 불일치·만료·형식 오류·클레임 검증 실패 — 클라이언트가 가진 토큰의 문제라 401.
            // 구체 사유는 남기지 않는다. 토큰 원문은 물론이고 "어디까지 맞았는지"도 공격자에게 줄 이유가 없다.
            log.info("ID 토큰 검증 실패 provider={}", provider);
            throw UserException.invalidIdToken(exception);
        } catch (JwtException exception) {
            // JWKS 조회 실패 등 provider 측 문제 — 재시도로 풀릴 수 있으므로 502 로 구분한다.
            //
            // 사유를 원인 체인에서 꺼내 남긴다. 클래스명만 찍으면 전부 JwtException 이라, 봇이 위조 토큰을
            // 뿌려 rate limit 에 걸린 것과 provider 가 실제로 죽은 것이 로그에서 같아 보인다 — 밤새 쌓인
            // 경고를 보고 "Google 이 죽었다" 로 읽게 된다.
            log.warn("provider 공개키 조회 실패 provider={} cause={}", provider, RootCause.label(exception));
            throw UserException.oidcProviderUnavailable(exception);
        }
    }

    /**
     * provider 전용 디코더를 만든다.
     *
     * <p>{@code createDefaultWithValidators} 로 감싸 Spring 이 기본으로 거는 검증(토큰 타입 · {@code exp}/{@code nbf}
     * · 인증서 thumbprint)을 그대로 살린 채 issuer·audience 검증을 얹는다. 기본 검증을 직접 조립하면 라이브러리가
     * 나중에 추가하는 것을 놓친다.
     */
    private static JwtDecoder buildDecoder(AuthProvider.Oidc oidc, List<String> audiences) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(oidc.jwksUri())
                .restOperations(jwksClient())
                .jwtProcessorCustomizer(processor -> processor.setJWSKeySelector(keySelector(oidc.jwksUri())))
                .build();
        decoder.setJwtValidator(tokenValidator(oidc.issuers(), audiences));
        return decoder;
    }

    /**
     * 서명을 뺀 나머지 검증 전부 — 기본 검증(토큰 타입·{@code exp}/{@code nbf})에 issuer·audience 를 얹는다.
     *
     * <p>서명은 {@link NimbusJwtDecoder} 가 JWKS 로 확인한다. 그 앞단 검증만 여기 모여 있어, 테스트가 네트워크
     * 없이 {@code aud}·{@code iss}·만료를 직접 확인할 수 있다 — 이 셋이 뚫리면 남의 앱 토큰으로 로그인이 된다.
     */
    static OAuth2TokenValidator<Jwt> tokenValidator(List<String> issuers, List<String> audiences) {
        return JwtValidators.createDefaultWithValidators(issuerValidator(issuers), audienceValidator(audiences));
    }

    /**
     * 공개키를 고르는 자리 — <b>기본 조립을 그대로 쓰지 않는다</b>.
     *
     * <p>기본값은 요청이 아는 키를 못 찾으면 <b>그때마다 JWKS 를 다시 받는다</b>. 키 선택은 서명 검증보다
     * 먼저라, 서명이 가짜인 토큰도 그 경로를 탄다. 이 엔드포인트는 인증 없이 열려 있어(로그인 전이니 당연하다)
     * 아무나 쓰레기 토큰을 던지면 던진 수만큼 우리가 provider 를 두드리고, 요청마다 톰캣 스레드가 물린다.
     * 실측으로 확인했다 — 모르는 {@code kid} 10회에 JWKS 10회, 위조 서명 10회에도 10회.
     *
     * <p>세 가지를 함께 건다.
     *
     * <ul>
     *   <li><b>rate limit</b> — 강제 갱신 사이 최소 간격. 위조 토큰이 조회를 유발해도 이 간격을 넘지 못한다.
     *   <li><b>선갱신</b> — 만료 전에 미리 받아 둔다. TTL 만 두면 만료 직후 요청이 조회를 뒤집어쓴다.
     *   <li><b>캐시 TTL</b> — 키 회전을 따라갈 만큼 짧게. 공개키는 자주 바뀌지 않지만 회전 자체는 일어난다.
     * </ul>
     */
    private static JWSKeySelector<SecurityContext> keySelector(String jwksUri) {
        try {
            // 상한을 여기서도 명시한다. create(URL) 만 쓰면 Nimbus 기본 retriever(각 500ms)가 붙어,
            // restOperations 에 준 3초가 이 경로에서는 죽은 설정이 된다 — 재측정 없이 8배 좁아지는 셈이다.
            DefaultResourceRetriever retriever = new DefaultResourceRetriever(
                    (int) JWKS_TIMEOUT.toMillis(), (int) JWKS_TIMEOUT.toMillis());
            JWKSource<SecurityContext> source = JWKSourceBuilder.<SecurityContext>create(
                            URI.create(jwksUri).toURL(), retriever)
                    .cache(JWKS_CACHE_TTL.toMillis(), JWKS_CACHE_REFRESH_TIMEOUT.toMillis())
                    .refreshAheadCache(JWKS_REFRESH_AHEAD.toMillis(), true)
                    .rateLimited(JWKS_MIN_REFRESH_INTERVAL.toMillis())
                    .build();
            // RS256 하나로 좁혀 둔다. Family.RSA 는 PS 계열까지 열리는데, 두 provider 가 쓰는 것은
            // RS256 이고 받을 알고리즘을 넓히는 것은 검증을 느슨하게 하는 쪽이다.
            return new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, source);
        } catch (MalformedURLException e) {
            // provider 상수라 여기 닿으면 코드 버그다 — 부팅 시점에 드러나는 편이 낫다.
            throw new IllegalStateException("JWKS 주소가 올바르지 않습니다 provider 설정을 확인하세요", e);
        }
    }

    /**
     * JWKS 를 받아올 때 쓰는 클라이언트 — <b>기본값을 그대로 두지 않는다</b>.
     *
     * <p>Spring Security 7 의 기본 연결·읽기 timeout 은 <b>30초</b>다. 이 조회는 로그인 요청 경로에서 일어나므로,
     * 제공자의 JWKS 엔드포인트가 멎으면 사용자가 30초를 기다린 뒤 실패한다. 그 사이 요청 스레드도 물려 있다.
     *
     * <p>3초로 잡는다 — 이 서비스가 외부 호출에 두는 상한과 같다. JWKS 는 정적 문서라 정상이면 수백 ms 에 온다.
     * 못 받으면 {@code USER-005}(502)로 끊고, 다음 요청이 다시 시도한다.
     */
    private static RestOperations jwksClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(JWKS_TIMEOUT);
        factory.setReadTimeout(JWKS_TIMEOUT);
        return new RestTemplate(factory);
    }

    /**
     * {@code iss} 가 이 provider 의 표기 중 하나여야 한다.
     *
     * <p>{@code JwtIssuerValidator} 를 쓰지 않는 이유는 그것이 값 하나만 받기 때문이다. Google 이 스킴 있는 표기와
     * 없는 표기를 모두 쓰는데, 하나만 허용하면 다른 표기를 받은 사용자가 전부 401 이 된다.
     *
     * <p>{@code getIssuer()}(URL) 가 아니라 클레임 문자열로 비교한다 — 스킴 없는 {@code accounts.google.com} 은
     * URL 로 해석되지 않아 비교 자체가 성립하지 않는다.
     */
    private static OAuth2TokenValidator<Jwt> issuerValidator(List<String> issuers) {
        return token -> {
            // iss 가 없으면 여기서 끝낸다. List.of 로 만든 불변 목록은 contains(null) 에서 NPE 를 던져,
            // 클레임을 비운 토큰 하나가 401 이 아니라 500 이 된다.
            String issuer = token.getClaimAsString(JwtClaimNames.ISS);
            if (issuer != null && issuers.contains(issuer)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", ISSUER_MISMATCH, null));
        };
    }

    /** aud 가 우리 클라이언트 ID 중 하나여야 한다 — 남의 앱용 토큰을 그대로 받아주면 계정 탈취가 된다. */
    private static OAuth2TokenValidator<Jwt> audienceValidator(List<String> audiences) {
        return token -> {
            List<String> tokenAudiences = token.getAudience();
            if (tokenAudiences != null && tokenAudiences.stream().anyMatch(audiences::contains)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", AUDIENCE_MISMATCH, null));
        };
    }

    private static String nicknameOf(AuthProvider.Oidc oidc, Jwt jwt) {
        return oidc.nicknameClaimIfPresent().map(jwt::getClaimAsString).orElse(null);
    }

    private static String emailOf(Jwt jwt) {
        return jwt.getClaimAsString(AuthProvider.EMAIL_CLAIM);
    }
}
