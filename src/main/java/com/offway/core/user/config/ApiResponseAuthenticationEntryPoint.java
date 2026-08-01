package com.offway.core.user.config;

import com.offway.core.common.exception.CommonErrorCode;
import com.offway.core.common.response.ApiResponseBody;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
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
 * <p>{@code WWW-Authenticate} 헤더는 붙이지 않는다. 붙이면 브라우저가 기본 인증 팝업을 띄우는데, 401 을
 * 받는 쪽 대부분이 API 클라이언트라 팝업이 오히려 방해가 된다. 사람이 Swagger 를 열 때는 Security 가 보호
 * 자원 접근 시점에 팝업을 띄운다.
 */
@Component
@RequiredArgsConstructor
public class ApiResponseAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // 실패 사유(비밀번호 틀림·계정 없음)는 응답에 담지 않는다 — 계정 존재 여부를 알려주는 셈이 된다.
        objectMapper.writeValue(response.getWriter(), ApiResponseBody.fail(CommonErrorCode.UNAUTHORIZED));
    }
}
