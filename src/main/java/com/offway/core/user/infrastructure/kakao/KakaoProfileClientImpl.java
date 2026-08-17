package com.offway.core.user.infrastructure.kakao;

import com.offway.core.user.domain.UserException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 카카오 프로필 조회 adapter — {@code GET /v2/user/me}, 인증은 {@code Authorization: Bearer <액세스 토큰>}.
 *
 * <p><b>이 호출은 캐시하지 않는다.</b> 캐시를 붙일 수 없는 게 아니라 붙이면 안 된다. 키가 액세스 토큰이라 사용자 수만큼
 * 무한히 늘고(캐시 키 공간 규칙), 무엇보다 신원 확인은 stale 이면 안 된다 — 이미 만료·해지된 토큰을 캐시가 유효하다고
 * 답하면 그게 곧 인증 우회다. 대신 로그인 1회당 호출 1회로 상한이 잡힌다.
 *
 * <p><b>client secret 은 쓰지 않는다.</b> 그 값이 필요한 곳은 인가 코드를 액세스 토큰으로 바꾸는 토큰 엔드포인트
 * ({@code POST /oauth/token}) 하나뿐인데, 그 단계는 앱이 SDK 로 이미 끝냈다. 이 호출은 액세스 토큰만 받는다.
 */
@Slf4j
@Component
class KakaoProfileClientImpl implements KakaoProfileClient {

    private static final String PROFILE_URL = "https://kapi.kakao.com/v2/user/me";

    /**
     * 토큰 정보 조회 — 이 토큰을 <b>어느 앱이 발급했는지</b>를 알려주는 유일한 경로.
     *
     * <p>프로필 조회로는 답할 수 없다. {@code /v2/user/me} 는 토큰이 유효하기만 하면 그 주인을 돌려주므로, 그것만
     * 믿으면 다른 카카오 앱의 토큰이 우리 로그인을 통과한다.
     */
    private static final String TOKEN_INFO_URL = "https://kapi.kakao.com/v1/user/access_token_info";

    /**
     * 호출 상한.
     *
     * <p>실측(2026-08-14, n=12, 인증 거부 경로): p90 27ms · 최대 30ms. 정상 프로필 조회는 카카오 쪽 저장소를 읽으므로
     * 이보다 느리고, 그 분포는 실 토큰이 없어 아직 못 쟀다. 그래서 실측 꼬리에 맞춰 좁히는 대신 <b>여유를 크게</b>
     * 잡았다 — 이 호출이 끊기면 로그인 자체가 실패해 사용자가 앱에 들어오지도 못하므로, 간헐 실패의 대가가 다른
     * 외부 호출(코스 품질 degrade)보다 훨씬 크다.
     *
     * <p>앱이 붙어 실 토큰으로 정상 응답 분포를 재면 p99 기준으로 다시 정하고 {@code docs/external-api-inventory.md}
     * 에 남긴다.
     *
     * <p><b>호출 하나의 상한이다.</b> 카카오 로그인은 토큰 정보 조회 + 프로필 조회 두 번을 순차로 부르므로 로그인
     * 하나의 최대 대기는 이 값의 두 배다. 둘 다 로그인 1회당 1번으로 상한이 잡혀 있어 팬아웃으로 곱해지지는 않는다.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    /** 파싱 실패 로그가 가리킬 응답 이름 — 두 호출이 같은 파싱 자리를 쓴다. */
    private static final String PROFILE_RESPONSE = "프로필 응답";

    private static final String TOKEN_INFO_RESPONSE = "토큰 정보 응답";

    private static final String ID_FIELD = "id";
    private static final String APP_ID_FIELD = "app_id";
    private static final String ACCOUNT_FIELD = "kakao_account";
    private static final String PROFILE_FIELD = "profile";
    private static final String NICKNAME_FIELD = "nickname";
    private static final String EMAIL_FIELD = "email";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    KakaoProfileClientImpl(WebClient externalWebClient, ObjectMapper objectMapper) {
        this.webClient = externalWebClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public KakaoProfile fetchProfile(String accessToken) {
        return parse(request(PROFILE_URL, accessToken));
    }

    /**
     * 토큰을 발급한 앱 번호를 조회한다.
     *
     * <p>{@code app_id} 가 없는 200 응답은 성공으로 넘기지 않는다 — 그 값이 없으면 "우리 앱 토큰인가" 에 답할 수
     * 없는데, 없는 것을 통과시키면 검증이 있으나 마나가 된다.
     */
    @Override
    public KakaoTokenInfo fetchTokenInfo(String accessToken) {
        return parseTokenInfo(request(TOKEN_INFO_URL, accessToken));
    }

    private String request(String url, String accessToken) {
        try {
            return webClient
                    .get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .block();
        } catch (WebClientResponseException.Unauthorized | WebClientResponseException.Forbidden exception) {
            // 카카오가 토큰을 거절했다 — 만료·해지·위조. 클라이언트가 가진 토큰의 문제라 401 로 내린다.
            // 카카오 응답 본문은 로그·예외 어디에도 싣지 않는다(토큰 조각이 섞여 올 수 있다).
            log.info("카카오 액세스 토큰 거부 status={}", exception.getStatusCode().value());
            throw UserException.invalidIdToken(exception);
        } catch (Exception exception) {
            // 그 밖의 실패(타임아웃·5xx·네트워크)는 재시도로 풀릴 수 있어 502 로 구분한다.
            // "네 토큰이 틀렸다"와 "카카오가 안 뜬다"는 앱이 취할 행동이 정반대다.
            // 어느 호출이 깨졌는지 알아야 하므로 주소를 남긴다 — 상수라 사용자 입력이 섞이지 않는다.
            log.warn("카카오 호출 실패 url={} cause={}", url, exception.getClass().getSimpleName());
            throw UserException.oidcProviderUnavailable(exception);
        }
    }

    /**
     * 응답에서 회원번호·닉네임·이메일을 꺼낸다.
     *
     * <p>회원번호가 없으면 <b>성공으로 넘기지 않는다.</b> 200 인데 신원이 없는 응답은 예외보다 위험하다 — 그대로 두면
     * 식별자 없이 가입이 진행되거나 엉뚱한 계정에 붙는데, 로그에는 아무 흔적이 남지 않는다.
     *
     * <p><b>package-private 은 테스트 seam 이다.</b> 통합 테스트는 이 클래스를 감싸는 port 를 stub 으로 갈아끼우므로
     * 파싱 코드가 한 번도 돌지 않는다. 카카오가 실제로 주는 JSON 모양(숫자 {@code id})을 직접 넣어 확인하려면
     * 여기에 닿을 수 있어야 한다.
     */
    KakaoProfile parse(String body) {
        if (body == null || body.isBlank()) {
            log.warn("카카오 프로필 응답이 비었다 — 200 이지만 신원을 확인할 수 없다");
            throw UserException.oidcProviderUnavailable(null);
        }
        JsonNode root = readTree(body, PROFILE_RESPONSE);
        String id = root.path(ID_FIELD).asString(null);
        if (id == null || id.isBlank()) {
            log.warn("카카오 프로필 응답에 회원번호가 없다 — 신원을 확인할 수 없다");
            throw UserException.oidcProviderUnavailable(null);
        }
        JsonNode account = root.path(ACCOUNT_FIELD);
        return new KakaoProfile(
                id,
                account.path(PROFILE_FIELD).path(NICKNAME_FIELD).asString(null),
                account.path(EMAIL_FIELD).asString(null));
    }

    /**
     * 응답에서 발급 앱 번호를 꺼낸다.
     *
     * <p><b>{@code app_id} 는 숫자로 온다.</b> 문자열로 오지 않으므로 강제 변환에 의존한다 — 그 변환이 깨지면
     * {@code appId} 가 {@code null} 이 되어 <b>카카오 로그인이 전부</b> 502 가 된다. 부팅도 되고 설정도 채워져
     * 있어 원인이 드러나지 않는 실패라, {@code parse} 와 같은 이유로 seam 을 열어 테스트로 잠갔다.
     */
    KakaoTokenInfo parseTokenInfo(String body) {
        if (body == null || body.isBlank()) {
            log.warn("카카오 토큰 정보 응답이 비었다 — 200 이지만 발급 앱을 확인할 수 없다");
            throw UserException.oidcProviderUnavailable(null);
        }
        JsonNode root = readTree(body, TOKEN_INFO_RESPONSE);
        String appId = root.path(APP_ID_FIELD).asString(null);
        if (appId == null || appId.isBlank()) {
            log.warn("카카오 토큰 정보 응답에 app_id 가 없다 — 우리 앱 토큰인지 확인할 수 없다");
            throw UserException.oidcProviderUnavailable(null);
        }
        return new KakaoTokenInfo(root.path(ID_FIELD).asString(null), appId);
    }

    /**
     * 두 응답이 함께 쓰는 파싱 자리 — <b>어느 응답이 깨졌는지 인자로 받는다.</b>
     *
     * <p>문구를 프로필로 고정해 두면 토큰 정보가 깨져도 로그가 프로필 조회를 가리킨다. 두 호출이 다른 이유로
     * 실패하는데 로그가 한쪽 이름만 대면, 원인을 찾을 때 엉뚱한 엔드포인트를 들여다보게 된다.
     */
    private JsonNode readTree(String body, String responseName) {
        try {
            return objectMapper.readTree(body);
        } catch (JacksonException exception) {
            // 응답 본문은 로그에 남기지 않는다 — 파싱에 실패한 문자열에 무엇이 섞여 있는지 알 수 없다.
            log.warn("카카오 {} 파싱 실패 cause={}", responseName, exception.getClass().getSimpleName());
            throw UserException.oidcProviderUnavailable(exception);
        }
    }
}
