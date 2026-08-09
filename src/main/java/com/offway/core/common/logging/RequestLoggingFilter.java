package com.offway.core.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청 하나를 <b>한 줄</b>로 남긴다.
 *
 * <pre>
 * 200 GET  /api/v1/regions/76/places?kind=STAY 0.11s dev · 숙소 42건
 * 201 POST /api/v1/courses?regionId=31 6.10s dev · 코스 26슬롯 ext=[tour 840ms×3]
 * </pre>
 *
 * <p><b>진입 줄을 따로 찍지 않는다.</b> 요청당 두 줄이면 운영 로그에서 사용자 흐름이 절반으로 희석된다.
 * 진입 줄의 값은 "응답이 안 끝난 요청" 을 보여주는 것 하나인데, 그건 드물고 소요시간으로도 짐작된다 —
 * 흐름을 훑는 일이 훨씬 잦으므로 그쪽을 택했다.
 *
 * <p>쿼리는 경로에 붙여 쓴다. 사람이 주소창에서 보는 모양 그대로라 별도 {@code q=[...]} 칸보다 빨리 읽힌다.
 *
 * <p>이 줄은 응답을 다 쓴 뒤 {@code finally} 에서 찍는다. 그래서 클라이언트가 중간에 끊어 broken pipe 가
 * 난 요청도 "몇 초 걸렸고 어떤 status 로 끝났는지" 가 남는다. 이게 없어서 스레드 이름으로 역추적해야 했던
 * 사건이 있다(2026-08-06 12:37).
 */
@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Set<String> SKIPPED_PREFIXES =
            Set.of("/actuator", "/swagger-ui", "/v3/api-docs", "/favicon.ico");

    private static final String ANONYMOUS = "anonymous";
    /** 추적 id 길이(hex). 한 번에 살아 있는 요청 수가 많지 않아 6자면 눈으로 구분하기에 충분하다. */
    private static final int TRACE_ID_BYTES = 3;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final String SECONDS_FORMAT = "%.2f";
    /** GET·POST 를 같은 폭으로 맞춰 경로가 세로로 정렬되게 한다. 훑을 때 눈이 경로를 따라 내려간다. */
    private static final String METHOD_FORMAT = "%-4s";
    private static final String QUERY_FORMAT = "?%s";
    private static final String EXTERNAL_CALLS_FRAGMENT_FORMAT = " ext=[%s]";
    /** 요약 앞의 가운뎃점 — 경로·시간·사용자(고정 정보)와 이번 요청의 결과를 눈으로 가른다. */
    private static final String SUMMARY_FRAGMENT_FORMAT = " · %s";
    private static final String SUMMARY_DELIMITER = " ";

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

        MDC.put(LogAttributes.TRACE_ID, newTraceId());
        try {
            chain.doFilter(request, response);
        } finally {
            // 사용자는 인증 필터가 채운 뒤에야 알 수 있어 여기서 넣는다. 요청 줄에 함께 실리도록
            // 로그를 찍기 전에 넣고, 아래 clear 로 같은 스레드의 다음 요청에 새지 않게 한다.
            MDC.put(LogAttributes.USER_ID, currentUser());
            double seconds = (System.nanoTime() - startedAt) / NANOS_PER_SECOND;
            log.info(
                    "{} {} {}{} {}s {}{}{}",
                    response.getStatus(),
                    METHOD_FORMAT.formatted(method),
                    path,
                    query.isEmpty() ? "" : QUERY_FORMAT.formatted(query),
                    SECONDS_FORMAT.formatted(seconds),
                    currentUser(),
                    summaries(request),
                    recorder.isEmpty() ? "" : EXTERNAL_CALLS_FRAGMENT_FORMAT.formatted(recorder.summary()));
            // **반드시 지운다.** 톰캣은 스레드를 재사용하므로, 안 지우면 다음 요청이 앞 요청의 추적 id 를
            // 그대로 달고 나간다 — 추적을 도우려던 것이 오히려 거짓 연결을 만든다.
            MDC.remove(LogAttributes.TRACE_ID);
            MDC.remove(LogAttributes.USER_ID);
        }
    }

    /** 짧은 hex 추적 id. 보안 토큰이 아니라 눈으로 묶는 표식이라 암호학적 난수가 필요 없다. */
    private static String newTraceId() {
        byte[] bytes = new byte[TRACE_ID_BYTES];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * 이 요청이 실제로 무엇을 주고받았는지 — 없으면 통째로 뺀다.
     *
     * <p>빈 칸을 남기지 않는 이유: {@code res=[]} 는 "결과가 없다" 와 "DTO 가 요약을 안 냈다" 를 뭉갠다.
     * 게다가 요약이 없는 요청이 대다수라, 빈 칸을 남기면 줄만 길어지고 정보는 0 이다.
     */
    private static String summaries(HttpServletRequest request) {
        String requestSummary = attribute(request, LogAttributes.REQUEST_SUMMARY);
        String responseSummary = attribute(request, LogAttributes.RESPONSE_SUMMARY);
        if (requestSummary == null && responseSummary == null) {
            return "";
        }
        if (requestSummary == null) {
            return SUMMARY_FRAGMENT_FORMAT.formatted(responseSummary);
        }
        if (responseSummary == null) {
            return SUMMARY_FRAGMENT_FORMAT.formatted(requestSummary);
        }
        return SUMMARY_FRAGMENT_FORMAT.formatted(requestSummary + SUMMARY_DELIMITER + responseSummary);
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
