package com.offway.core.user.config;

import com.offway.core.common.exception.CommonErrorCode;
import com.offway.core.common.response.ApiResponseBody;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
 * <p><b>{@code WWW-Authenticate} 헤더를 직접 붙인다.</b> 이 엔트리 포인트가 Security 의 기본 Basic
 * 엔트리 포인트를 <b>대체</b>하므로, 여기서 안 붙이면 헤더가 아예 나가지 않는다 — 그러면 브라우저가 인증
 * 팝업을 띄우지 않아 사람이 Swagger 를 열 수단이 사라진다(로그인 화면도 없다). 팝업으로 통과하는 것이
 * Basic 을 고른 이유 중 하나라, 헤더가 빠지면 그 선택의 근거가 무너진다.
 *
 * <p>앱 클라이언트는 이 헤더의 영향을 받지 않는다 — 팝업은 브라우저의 동작이고, 앱은 헤더를 무시한 채
 * 응답 본문만 읽는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiResponseAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /** 브라우저가 인증 팝업을 띄우게 하는 challenge. realm 은 팝업에 표시되는 이름이다. */
    private static final String BASIC_CHALLENGE = "Basic realm=\"OffWay\", charset=\"UTF-8\"";

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        // 게이트를 뚫으려는 시도를 나중에라도 파악할 수 있게 흔적을 남긴다. 사용자명·자격증명은 절대 남기지
        // 않는다 — 오타로 비밀번호가 username 자리에 들어오는 일이 흔하고, 그게 그대로 로그에 박힌다.
        // 레벨은 info 다: 401 은 클라이언트 계약 위반이라 서버 입장에서는 정상 흐름이다(로깅 규약).
        log.info("인증 실패 — 401 method={} path={}", request.getMethod(), request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BASIC_CHALLENGE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // 실패 사유(비밀번호 틀림·계정 없음)는 응답에 담지 않는다 — 계정 존재 여부를 알려주는 셈이 된다.
        objectMapper.writeValue(response.getWriter(), ApiResponseBody.fail(CommonErrorCode.UNAUTHORIZED));
    }
}
