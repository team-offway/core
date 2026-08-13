package com.offway.core.user.config;

import com.offway.core.common.exception.CommonErrorCode;
import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.user.domain.UserErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 인증 실패(401)를 공통 응답 래퍼로 내린다.
 *
 * <p>Security 의 기본 401 은 <b>필터 레벨</b>이라 {@code @RestControllerAdvice} 를 타지 않는다 — 그대로 두면
 * 본문이 비어 클라이언트가 파싱에 실패한다. 모든 응답이 {@link ApiResponseBody} 라는 계약을 이 경로에서도
 * 지킨다(exception-and-response 규약).
 *
 * <p><b>제시한 자격증명 종류에 따라 code 를 나눈다.</b> 401 하나로 뭉치면 앱이 다음에 뭘 해야 할지 모른다.
 *
 * <table>
 *   <tr><th>요청이 들고 온 것</th><th>code</th><th>클라이언트가 할 일</th></tr>
 *   <tr><td>{@code Bearer} (만료·위조된 access)</td><td>{@code USER-004}</td><td>refresh 로 재발급</td></tr>
 *   <tr><td>그 외(없음 · Basic)</td><td>{@code COMMON-401}</td><td>자격증명 제시 — 브라우저는 팝업</td></tr>
 * </table>
 *
 * <p>Bearer 를 들고 온 요청에 재발급을 시킬 수 있는 건 이 구분 덕이다. 반대로 아무것도 안 들고 온 요청에
 * {@code USER-004} 를 주면, 있지도 않은 refresh 로 재발급을 시도하는 무한 루프가 된다.
 *
 * <p><b>{@code WWW-Authenticate} 헤더를 직접 붙인다.</b> 이 엔트리 포인트가 Security 의 기본 Basic
 * 엔트리 포인트를 <b>대체</b>하므로, 여기서 안 붙이면 헤더가 아예 나가지 않는다 — 그러면 브라우저가 인증
 * 팝업을 띄우지 않아 사람이 Swagger 를 열 수단이 사라진다(로그인 화면도 없다). 팝업으로 통과하는 것이
 * Basic 을 고른 이유 중 하나라, 헤더가 빠지면 그 선택의 근거가 무너진다.
 *
 * <p>Bearer 로 온 요청에는 이 헤더를 붙이지 않는다. 앱에게 Basic 을 권할 이유가 없고, 앱은 어차피 헤더를
 * 무시한 채 응답 본문만 읽는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiResponseAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /** 브라우저가 인증 팝업을 띄우게 하는 challenge. realm 은 팝업에 표시되는 이름이다. */
    private static final String BASIC_CHALLENGE = "Basic realm=\"OffWay\", charset=\"UTF-8\"";

    /** 이 접두어로 시작하는 경로만 우리 API 다. 나머지 401 은 스캐너 소음으로 본다. */
    private static final String API_PATH_PREFIX = "/api/";

    private static final String BEARER_PREFIX = "Bearer ";

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        logAttempt(request);
        // 앱이 access 토큰을 들고 왔는데 통과하지 못했다 — 만료됐거나 위조다. 재발급하라는 신호를 준다.
        if (bearerPresented(request)) {
            SecurityErrorResponder.write(objectMapper, response, UserErrorCode.INVALID_ACCESS_TOKEN);
            return;
        }
        // 헤더는 본문보다 먼저 세팅한다 — 본문을 쓰면 응답이 커밋돼 헤더 변경이 반영되지 않는다.
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BASIC_CHALLENGE);
        // 실패 사유(비밀번호 틀림·계정 없음)는 응답에 담지 않는다 — 계정 존재 여부를 알려주는 셈이 된다.
        SecurityErrorResponder.write(objectMapper, response, CommonErrorCode.UNAUTHORIZED);
    }

    private static boolean bearerPresented(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null && header.startsWith(BEARER_PREFIX);
    }

    /**
     * 게이트를 뚫으려는 시도를 나중에라도 파악할 수 있게 흔적을 남긴다. 자격증명은 절대 남기지 않는다 — 오타로
     * 비밀번호가 username 자리에 들어오는 일이 흔하고, 그게 그대로 로그에 박힌다. 토큰도 마찬가지다.
     *
     * <p>레벨은 info 다: 401 은 클라이언트 계약 위반이라 서버 입장에서는 정상 흐름이다(로깅 규약). 우리 API
     * 경로가 아닌 401 은 debug 다. 공인 IP 에 붙은 서버라 {@code /Login}·{@code /wp-admin} 같은 스캐너가 쉬지
     * 않고 두드리는데, 그걸 info 로 두면 정작 봐야 할 사용자 요청 로그가 그 사이에 묻힌다.
     */
    private static void logAttempt(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith(API_PATH_PREFIX)) {
            log.info("인증 실패 — 401 method={} path={}", request.getMethod(), path);
        } else {
            log.debug("인증 실패(비 API 경로) — 401 method={} path={}", request.getMethod(), path);
        }
    }
}
