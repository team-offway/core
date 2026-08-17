package com.offway.core.user.config;

import com.offway.core.common.exception.ErrorCode;
import com.offway.core.common.response.ApiResponseBody;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring Security 단계의 실패를 공통 응답 래퍼로 직접 써 내린다.
 *
 * <p>이 경로는 {@code DispatcherServlet} 밖이라 {@code GlobalExceptionHandler} 가 관여하지 못한다. 컨트롤러
 * 경로와 응답 모양이 한 글자도 다르지 않아야 FE 가 실패 처리 분기를 하나로 유지할 수 있다.
 */
final class SecurityErrorResponder {

    private SecurityErrorResponder() {}

    static void write(ObjectMapper objectMapper, HttpServletResponse response, ErrorCode errorCode)
            throws IOException {
        response.setStatus(errorCode.category().httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ApiResponseBody.fail(errorCode));
    }
}
