package com.offway.core.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
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

    private static final String ANONYMOUS = "anon";

    /**
     * 로그에 남길 사용자 식별자 앞자리 수(#41).
     *
     * <p>UUID 라 8자면 사실상 유일하다 — 같은 앞자리를 가진 둘을 만나려면 수만 개가 필요한데 우리 사용자
     * 수는 그 근처도 아니다. 로그 패턴이 이 폭({@code %-8.8X}) 을 전제로 칸을 잡는다.
     */
    private static final int USER_ID_PREFIX_LENGTH = 8;
    /** 추적 id 길이(hex). 한 번에 살아 있는 요청 수가 많지 않아 6자면 눈으로 구분하기에 충분하다. */
    private static final int TRACE_ID_BYTES = 3;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final String SECONDS_FORMAT = "%.2f";
    /** GET·POST 를 같은 폭으로 맞춰 경로가 세로로 정렬되게 한다. 훑을 때 눈이 경로를 따라 내려간다. */
    private static final String METHOD_FORMAT = "%-4s";
    /**
     * 파라미터를 경로 뒤에 대괄호로 붙인다 — {@code GET /api/v1/air [region=충청남도]}.
     *
     * <p>예전에는 {@code ?} 뒤에 인코딩된 원문을 그대로 붙였다. 한글이 {@code %ec%b6%a9...} 으로 나가
     * 눈으로 못 읽었고, 경로와 붙어 있어 경로만 훑기도 어려웠다.
     */
    private static final String PARAMS_FORMAT = " [%s]";
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
        String params = SensitiveParams.readableParams(request.getQueryString());
        long startedAt = System.nanoTime();

        MDC.put(LogAttributes.TRACE_ID, newTraceId());
        // **요청이 시작될 때 넣는다**(#41). 예전에는 응답을 다 쓴 뒤 finally 에서 넣어, 정작 그 요청 중에
        // 난 로그에는 붙지 않았다 — 그러면 MDC 에 둘 이유가 없다. 이 필터는 보안 필터보다 뒤라 인증
        // 컨텍스트가 이미 채워져 있어, 여기서 읽으면 Bearer·Basic 이 모두 잡힌다.
        MDC.put(LogAttributes.USER_ID, currentUser());
        try {
            chain.doFilter(request, response);
        } finally {
            double seconds = (System.nanoTime() - startedAt) / NANOS_PER_SECOND;
            log.info(
                    "{} {} {}{} {}s{}{}",
                    response.getStatus(),
                    METHOD_FORMAT.formatted(method),
                    path,
                    params.isEmpty() ? "" : PARAMS_FORMAT.formatted(params),
                    SECONDS_FORMAT.formatted(seconds),
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

    /**
     * 요청을 보낸 주체 — Bearer 면 사용자 식별자 <b>앞자리</b>, Basic 이면 계정 이름, 아니면 {@value #ANONYMOUS}.
     *
     * <p><b>UUID 를 통째로 싣지 않는다.</b> 36자가 매 줄에 박히면 정작 읽어야 할 경로·메시지가 밀려난다.
     * 앞자리 {@value #USER_ID_PREFIX_LENGTH} 자면 로그끼리 묶고 DB 에서 {@code LIKE 'xxxxxxxx%'} 로 되짚기에
     * 충분하다 — 전문이 필요한 자리는 로그인 성공 줄 하나뿐이고 거기서는 전문을 남긴다.
     *
     * <p>Basic 계정 이름은 자르지 않는다. 이미 짧고, 앞자리만 남기면 어느 계정인지 오히려 알 수 없어진다.
     */
    private static String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // **익명 토큰도 '인증됨' 이다.** Spring Security 는 비인증 요청에 AnonymousAuthenticationToken 을
        // 끼워 넣는데 그 isAuthenticated() 가 true 라, 그것만 보면 principal 이름("anonymousUser")이
        // 신원 칸에 실린다 — 실제로 로그에 `mousUser`(패턴 폭에 잘린 값)로 찍혔다.
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ANONYMOUS;
        }
        // Bearer 로 들어온 요청은 principal 이 UUID 다(JwtAuthenticationFilter 가 넣는다).
        if (authentication.getPrincipal() instanceof UUID userId) {
            return shortId(userId);
        }
        return authentication.getName();
    }

    /** UUID 앞자리 — 하이픈 앞 첫 마디가 그대로 이 길이다. */
    private static String shortId(UUID userId) {
        String text = userId.toString();
        return text.length() <= USER_ID_PREFIX_LENGTH ? text : text.substring(0, USER_ID_PREFIX_LENGTH);
    }
}
