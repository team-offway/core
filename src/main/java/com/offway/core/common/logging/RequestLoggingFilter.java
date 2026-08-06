package com.offway.core.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청 하나를 {@code →}·{@code ←} 두 줄로 남긴다.
 *
 * <p><b>한 줄이다.</b> 여러 줄로 하면 {@code --grep} 이 안 걸리고, 터미널 컬러라이저의 칸 정렬이 깨지고,
 * 나중에 구조화 로그로 옮길 때 파싱이 어렵다.
 *
 * <p>{@code ←} 는 응답을 다 쓴 뒤 {@code finally} 에서 찍는다. 그래서 클라이언트가 중간에 끊어
 * broken pipe 가 난 요청도 "몇 초 걸렸고 어떤 status 로 끝났는지" 가 남는다. 이게 없어서 스레드 이름으로
 * 역추적해야 했던 사건이 있다(2026-08-06 12:37).
 *
 * <p>{@code ←} 줄에도 {@code q=[...]} 를 다시 싣는다. 상관관계 ID 가 없어 동시 요청이 뒤섞이면
 * {@code →} 줄만으로는 이 {@code ←} 가 어떤 요청의 응답인지 못 짚는다 — {@code ←} 줄 하나로 완결되게
 * 중복을 감수한다.
 */
@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Set<String> SKIPPED_PREFIXES =
            Set.of("/actuator", "/swagger-ui", "/v3/api-docs", "/favicon.ico");

    private static final String ANONYMOUS = "anonymous";
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final String SECONDS_FORMAT = "%.2f";
    private static final String QUERY_FRAGMENT_FORMAT = " q=[%s]";
    private static final String EXTERNAL_CALLS_FRAGMENT_FORMAT = " ext=[%s]";
    private static final String REQUEST_SUMMARY_FRAGMENT_FORMAT = " req=[%s]";
    private static final String RESPONSE_SUMMARY_FRAGMENT_FORMAT = " res=[%s]";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return SKIPPED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        ExternalCallRecorder recorder = new ExternalCallRecorder();
        request.setAttribute(LogAttributes.EXTERNAL_CALLS, recorder);

        String method = request.getMethod();
        String path = request.getRequestURI();
        String query = SensitiveParams.maskQueryString(request.getQueryString());
        long startedAt = System.nanoTime();

        if (query.isEmpty()) {
            log.info("→ {} {}", method, path);
        } else {
            log.info("→ {} {}?{}", method, path, query);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            double seconds = (System.nanoTime() - startedAt) / NANOS_PER_SECOND;
            log.info(
                    "← {} {} {} {}s user={}{}{}{}",
                    response.getStatus(),
                    method,
                    path,
                    SECONDS_FORMAT.formatted(seconds),
                    currentUser(),
                    query.isEmpty() ? "" : QUERY_FRAGMENT_FORMAT.formatted(query),
                    recorder.isEmpty() ? "" : EXTERNAL_CALLS_FRAGMENT_FORMAT.formatted(recorder.summary()),
                    summaries(request));
        }
    }

    /** req=[...] res=[...] — 없는 쪽은 통째로 뺀다. 빈 대괄호는 "요약이 없다" 와 "DTO 가 안 냈다" 를 뭉갠다. */
    private static String summaries(HttpServletRequest request) {
        String requestSummary = attribute(request, LogAttributes.REQUEST_SUMMARY);
        String responseSummary = attribute(request, LogAttributes.RESPONSE_SUMMARY);
        return (requestSummary == null ? "" : REQUEST_SUMMARY_FRAGMENT_FORMAT.formatted(requestSummary))
                + (responseSummary == null ? "" : RESPONSE_SUMMARY_FRAGMENT_FORMAT.formatted(responseSummary));
    }

    private static String attribute(HttpServletRequest request, String key) {
        Object value = request.getAttribute(key);
        return value instanceof String text ? text : null;
    }

    private static String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ANONYMOUS;
        }
        return authentication.getName();
    }
}
