package com.offway.core.user.infrastructure.kakao;

import com.offway.core.user.config.AuthProperties;
import com.offway.core.user.domain.OAuthState;
import com.offway.core.user.domain.UserException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 카카오 웹 로그인 adapter — 인가 주소 조립과 {@code POST /oauth/token}.
 *
 * <p><b>도메인이 둘이다.</b> 인가·교환은 {@code kauth.kakao.com}(인증 서버)이고, 프로필·토큰정보는
 * {@code kapi.kakao.com}(API 서버)다. 한쪽이 죽어도 다른 쪽은 살 수 있어, 실패 로그가 어느 서버였는지
 * 구분되게 주소를 남긴다.
 *
 * <p><b>교환 응답을 캐시하지 않는다.</b> 인가 코드는 1회용이라 같은 키가 두 번 오지 않는다 — 캐시가
 * 적중할 수 없고, 적중한다면 그것 자체가 코드 재사용이라는 이상 신호다.
 */
@Slf4j
@Component
class KakaoOAuthClientImpl implements KakaoOAuthClient {

    private static final String AUTHORIZE_URL = "https://kauth.kakao.com/oauth/authorize";
    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";

    private static final String CLIENT_ID_PARAM = "client_id";
    private static final String REDIRECT_URI_PARAM = "redirect_uri";
    private static final String RESPONSE_TYPE_PARAM = "response_type";
    private static final String STATE_PARAM = "state";
    private static final String GRANT_TYPE_PARAM = "grant_type";
    private static final String CODE_PARAM = "code";
    private static final String CLIENT_SECRET_PARAM = "client_secret";

    /** 인가 코드 방식 — 토큰이 브라우저 주소창을 지나가지 않는 유일한 방식이다. */
    private static final String CODE_RESPONSE_TYPE = "code";

    private static final String AUTHORIZATION_CODE_GRANT = "authorization_code";

    private static final String ACCESS_TOKEN_FIELD = "access_token";

    /** 파싱 실패 로그가 가리킬 응답 이름 — {@code KakaoProfileClientImpl} 과 같은 표기를 쓴다. */
    private static final String TOKEN_RESPONSE = "토큰 교환 응답";

    /**
     * 호출 상한 — 프로필 조회와 같은 3초.
     *
     * <p>같은 값을 쓰는 이유는 <b>실패의 대가가 같기</b> 때문이다. 이 호출이 끊기면 로그인이 통째로 실패해
     * 어드민이 백오피스에 들어오지 못한다. 분포를 따로 재서 좁히는 것보다 여유를 두는 편이 낫다.
     *
     * <p><b>웹 로그인 한 번의 최대 대기는 이 값의 세 배다</b> — 교환 · 토큰정보 · 프로필을 순차로 부른다.
     * 앱 로그인(두 번)보다 한 단계 길다. 셋 다 로그인 1회당 1번이라 팬아웃으로 곱해지지는 않는다.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final AuthProperties authProperties;

    KakaoOAuthClientImpl(WebClient externalWebClient, ObjectMapper objectMapper, AuthProperties authProperties) {
        this.webClient = externalWebClient;
        this.objectMapper = objectMapper;
        this.authProperties = authProperties;
    }

    /**
     * 동의 화면 주소.
     *
     * <p>{@code redirect_uri} 는 교환 단계와 <b>같은 설정값</b>을 쓴다. 카카오가 두 단계에서 대조하므로
     * 여기서만 다르게 만들면 마지막에 {@code KOE006} 으로 실패한다.
     */
    @Override
    public String authorizationUri(OAuthState state) {
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam(CLIENT_ID_PARAM, authProperties.kakaoRestApiKey())
                .queryParam(REDIRECT_URI_PARAM, authProperties.kakaoRedirectUri())
                .queryParam(RESPONSE_TYPE_PARAM, CODE_RESPONSE_TYPE)
                .queryParam(STATE_PARAM, state.value())
                .encode()
                .toUriString();
    }

    @Override
    public String exchange(String authorizationCode) {
        return parse(request(authorizationCode));
    }

    /**
     * 교환 요청 본문 — {@code application/x-www-form-urlencoded}.
     *
     * <p>{@code client_secret} 은 <b>설정된 경우에만</b> 싣는다. 콘솔에서 Client Secret 을 안 켠 앱에 이
     * 값을 보내면 오히려 거절되므로, 빈 문자열로라도 채우면 안 된다.
     *
     * <p>{@code redirect_uri} 를 여기서도 보내는 이유는 카카오가 <b>인가 때 받은 값과 같은지 대조</b>하기
     * 때문이다. 코드를 가로챈 쪽이 자기 주소로 토큰을 받아 가는 것을 막는 장치다.
     */
    private MultiValueMap<String, String> body(String authorizationCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add(GRANT_TYPE_PARAM, AUTHORIZATION_CODE_GRANT);
        form.add(CLIENT_ID_PARAM, authProperties.kakaoRestApiKey());
        form.add(REDIRECT_URI_PARAM, authProperties.kakaoRedirectUri());
        form.add(CODE_PARAM, authorizationCode);
        authProperties.kakaoClientSecret().ifPresent(secret -> form.add(CLIENT_SECRET_PARAM, secret));
        return form;
    }

    private String request(String authorizationCode) {
        try {
            return webClient
                    .post()
                    .uri(TOKEN_URL)
                    .body(BodyInserters.fromFormData(body(authorizationCode)))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .block();
        } catch (WebClientResponseException.BadRequest | WebClientResponseException.Unauthorized exception) {
            // 카카오가 코드를 거절했다 — 만료·이미 쓴 코드·redirect_uri 불일치·client_secret 불일치.
            // 응답 본문은 남기지 않는다. 사유와 함께 우리 client_id 조각이 실려 올 수 있다.
            log.info("카카오 인가 코드 교환 거부 status={}", exception.getStatusCode().value());
            throw UserException.invalidIdToken(exception);
        } catch (Exception exception) {
            // 타임아웃·5xx·네트워크. 다시 시도하면 풀릴 수 있는 실패라 코드 거절과 구분한다.
            log.warn("카카오 인가 서버 호출 실패 url={} cause={}", TOKEN_URL, exception.getClass().getSimpleName());
            throw UserException.oidcProviderUnavailable(exception);
        }
    }

    /**
     * 응답에서 액세스 토큰을 꺼낸다.
     *
     * <p><b>토큰이 없는 200 을 성공으로 넘기지 않는다.</b> 그대로 두면 {@code null} 을 들고 프로필 조회로
     * 내려가 한 단계 뒤에서 엉뚱한 이유로 실패한다 — 로그가 "프로필 조회 실패" 를 가리켜, 원인을 찾을 때
     * 잘못된 엔드포인트를 들여다보게 된다.
     *
     * <p><b>package-private 은 테스트 seam 이다.</b> 통합 테스트는 이 클래스를 감싸는 port 를 stub 으로
     * 갈아끼우므로 파싱 코드가 한 번도 돌지 않는다({@code KakaoProfileClientImpl} 과 같은 이유).
     */
    String parse(String body) {
        if (body == null || body.isBlank()) {
            log.warn("카카오 토큰 교환 응답이 비었다 — 200 이지만 액세스 토큰이 없다");
            throw UserException.oidcProviderUnavailable(null);
        }
        JsonNode root = readTree(body);
        String accessToken = root.path(ACCESS_TOKEN_FIELD).asString(null);
        if (accessToken == null || accessToken.isBlank()) {
            log.warn("카카오 토큰 교환 응답에 액세스 토큰이 없다");
            throw UserException.oidcProviderUnavailable(null);
        }
        return accessToken;
    }

    private JsonNode readTree(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JacksonException exception) {
            // 응답 본문은 로그에 남기지 않는다 — 파싱에 실패한 문자열에 토큰 조각이 섞여 있을 수 있다.
            log.warn("카카오 {} 파싱 실패 cause={}", TOKEN_RESPONSE, exception.getClass().getSimpleName());
            throw UserException.oidcProviderUnavailable(exception);
        }
    }
}
