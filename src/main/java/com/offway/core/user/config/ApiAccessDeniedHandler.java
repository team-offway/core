package com.offway.core.user.config;

import com.offway.core.common.exception.CommonErrorCode;
import com.offway.core.common.logging.SensitiveParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 인증됐으나 권한이 없을 때의 403 — 공통 래퍼 규격으로 내린다.
 *
 * <p>지금은 권한·롤이 없어 실질적으로 타지 않지만, 등록해두지 않으면 롤이 생기는 순간 403 만 래퍼 밖으로 새는 문제가
 * 그대로 재발한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        logAttempt(request);
        SecurityErrorResponder.write(objectMapper, response, CommonErrorCode.FORBIDDEN);
    }

    /**
     * 403 은 401 과 성격이 다르다 — <b>누구인지는 아는데 권한이 없는 것</b>이라, 남길 신원이 실제로 있다(#41).
     *
     * <p>여기 찍히는 대부분은 Basic 계정으로 쓰기를 시도한 경우다(Swagger 에서 POST 를 눌렀거나, 스모크가
     * 읽기 경로를 벗어났거나). 사람이 고칠 수 있는 실수라 누구였는지가 곧 대응이다.
     *
     * <p>401 과 달리 요청 줄이 함께 남으므로 출발지는 중복해서 싣지 않는다.
     */
    private static void logAttempt(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.info(
                "권한 없음 — 403 method={} path={} principal={}",
                request.getMethod(),
                request.getRequestURI(),
                authentication == null ? "none" : SensitiveParams.forLog(authentication.getName()));
    }
}
