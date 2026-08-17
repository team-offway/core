package com.offway.core.user.config;

import com.offway.core.common.exception.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 인증됐으나 권한이 없을 때의 403 — 공통 래퍼 규격으로 내린다.
 *
 * <p>지금은 권한·롤이 없어 실질적으로 타지 않지만, 등록해두지 않으면 롤이 생기는 순간 403 만 래퍼 밖으로 새는 문제가
 * 그대로 재발한다.
 */
@Component
@RequiredArgsConstructor
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        SecurityErrorResponder.write(objectMapper, response, CommonErrorCode.FORBIDDEN);
    }
}
