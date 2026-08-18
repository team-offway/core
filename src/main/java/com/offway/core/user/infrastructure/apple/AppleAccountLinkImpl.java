package com.offway.core.user.infrastructure.apple;

import com.offway.core.common.logging.RootCause;
import com.offway.core.user.config.AuthProperties;
import com.offway.core.user.domain.AuthProvider;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Apple 계정 연결 adapter — {@code /auth/token} 교환과 {@code /auth/revoke}(#287).
 *
 * <p><b>자격이 없으면 조용히 비활성이다.</b> {@code .p8} 없이도 부팅·로그인이 되는 것이 이 레포의 불변식이라
 * (CLAUDE.md 로컬 실행성) 예외를 던지지 않는다. 대신 <b>왜 못 했는지는 남긴다</b> — degrade 가 정상처럼
 * 보이면 심사 항목이 빠진 채로 배포된 것을 아무도 모른다.
 *
 * <p><b>어느 쪽도 예외를 올리지 않는다.</b> 교환은 로그인 경로에, 해제는 탈퇴 경로에 붙는다. 둘 다 실패가
 * 그 행위 자체를 막아서는 안 된다 — 로그인이 안 되는 것, 계정을 못 지우는 것이 연결이 남는 것보다 나쁘다.
 */
@Slf4j
@Component
class AppleAccountLinkImpl implements AppleAccountLink {

    private static final String TOKEN_URL = "https://appleid.apple.com/auth/token";

    private static final String REVOKE_URL = "https://appleid.apple.com/auth/revoke";

    /**
     * 호출 상한.
     *
     * <p>3초다 — 이 서비스가 외부 호출에 두는 상한과 같다. 교환은 로그인 경로에 붙어 사용자가 기다리므로
     * 길게 둘 수 없고, 해제는 탈퇴 응답을 붙잡는다. 못 받으면 그 한 번을 포기하고 로그를 남긴다.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    /** Apple 이 refresh 토큰을 담아 주는 필드. */
    private static final String REFRESH_TOKEN_FIELD = "refresh_token";

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AppleClientSecret clientSecret;

    AppleAccountLinkImpl(WebClient externalWebClient, AuthProperties authProperties) {
        this.webClient = externalWebClient;
        this.clientSecret =
                new AppleClientSecret(authProperties.apple(), authProperties.audiencesOf(AuthProvider.APPLE));
        if (!clientSecret.available()) {
            // 부팅을 막지 않되 한 번은 크게 남긴다 — 운영에 이 로그가 보이면 심사 항목이 빠진 채 떠 있는 것이다.
            log.warn("Apple 연결 해제 자격이 없습니다 — 탈퇴해도 Apple '이 App으로 로그인' 목록에 남습니다"
                    + " (offway.auth.apple.team-id · key-id · private-key-base64)");
        }
    }

    @Override
    public List<String> clientIds() {
        return clientSecret.clientIds();
    }

    @Override
    public Optional<String> exchange(String authorizationCode, String clientId) {
        if (!clientSecret.available() || isBlank(authorizationCode)) {
            return Optional.empty();
        }
        MultiValueMap<String, String> form = form(clientId);
        form.add("grant_type", "authorization_code");
        form.add("code", authorizationCode);
        return post(TOKEN_URL, form, "토큰 교환")
                .map(body -> objectMapper.readTree(body).path(REFRESH_TOKEN_FIELD).asString(null))
                .filter(token -> !isBlank(token));
    }

    @Override
    public boolean revoke(String refreshToken, String clientId) {
        if (!clientSecret.available() || isBlank(refreshToken)) {
            return false;
        }
        MultiValueMap<String, String> form = form(clientId);
        form.add("token", refreshToken);
        form.add("token_type_hint", REFRESH_TOKEN_FIELD);
        // revoke 는 성공 시 200 에 빈 본문이라, 본문이 아니라 예외 없음이 성공 신호다.
        return post(REVOKE_URL, form, "연결 해제").isPresent();
    }

    private MultiValueMap<String, String> form(String clientId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret.issue(clientId));
        return form;
    }

    /**
     * @return 응답 본문. 빈 본문도 성공이므로 {@code Optional.of("")} 이고, 실패만 {@code empty} 다
     */
    private Optional<String> post(String url, MultiValueMap<String, String> form, String what) {
        try {
            String body = webClient
                    .post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .block();
            return Optional.of(body == null ? "" : body);
        } catch (RuntimeException exception) {
            // 응답 본문을 남기지 않는다 — Apple 오류 본문에 우리 client secret 조각이 실려 올 수 있다.
            // 사유는 남긴다: 자격이 거절된 것(invalid_client)과 코드가 만료된 것(invalid_grant)은
            // 대응이 완전히 다른데, 아무것도 안 남기면 둘을 구분할 방법이 없다.
            log.warn("Apple {} 실패 — 계속 진행합니다 cause={}", what, RootCause.label(exception));
            return Optional.empty();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
