package com.offway.core.common.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 요청 하나에 신원을 실어 나르는 필터(#41).
 *
 * <p><b>여기서 잠그는 것은 "언제" 다.</b> 값이 요청 줄에만 있으면 MDC 에 둘 이유가 없다 — 추적의 값은
 * 그 요청 <b>도중에</b> 난 다른 로그(외부 호출 실패 warn, 예외 스택)를 같은 값으로 묶는 데 있다. 그래서
 * 체인이 도는 순간의 MDC 를 직접 들여다본다.
 *
 * <p>Spring 을 띄우지 않는다. "어느 엔드포인트가 마침 로그를 찍는가" 에 기대면, 그 엔드포인트가 조용해지는
 * 날 이 테스트가 <b>아무것도 검증하지 않은 채</b> 통과한다.
 */
class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void 체인이_도는_동안_추적id_와_신원이_MDC_에_있다() throws Exception {
        authenticateAs(UUID.fromString("3f2a9c81-4b7d-4e0a-9f21-0c7d5e8b1234"));
        String[] seen = new String[2];

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/courses"), new MockHttpServletResponse(),
                (req, res) -> {
                    seen[0] = MDC.get(LogAttributes.TRACE_ID);
                    seen[1] = MDC.get(LogAttributes.USER_ID);
                });

        assertNotNull(seen[0], "체인이 도는 동안 추적 id 가 없다");
        // 전문이다. 앞 8자만 싣던 때가 있었는데, 우리 식별자는 첫 마디가 호스트 IP 라 모든 사용자가
        // 같은 앞자리로 시작해 구분이 안 됐다.
        assertEquals("3f2a9c81-4b7d-4e0a-9f21-0c7d5e8b1234", seen[1]);
    }

    @Test
    void 요청이_끝나면_지운다() throws Exception {
        authenticateAs(UUID.randomUUID());

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/courses"), new MockHttpServletResponse(),
                (req, res) -> {});

        // 톰캣은 스레드를 재사용한다. 안 지우면 다음 요청이 앞 요청의 신원을 그대로 달고 나가 —
        // 추적을 도우려던 값이 거짓 연결을 만든다. 아무것도 없는 것보다 나쁘다.
        assertNull(MDC.get(LogAttributes.TRACE_ID));
        assertNull(MDC.get(LogAttributes.USER_ID));
    }

    @Test
    void 인증되지_않은_요청은_anon_이다() throws Exception {
        String[] seen = new String[1];

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/public/courses/tok"),
                new MockHttpServletResponse(), (req, res) -> seen[0] = MDC.get(LogAttributes.USER_ID));

        // 빈 값이 아니라 anon 이다 — 로그 칸이 비면 "비인증" 과 "값을 못 넣었다" 가 구분되지 않는다.
        assertEquals("anon", seen[0]);
    }

    @Test
    void 익명_토큰도_anon_이다() throws Exception {
        // **Spring Security 는 비인증 요청에 익명 토큰을 끼워 넣고, 그 isAuthenticated() 는 true 다.**
        // 그것만 보면 principal 이름("anonymousUser")이 신원 칸에 실린다 — 실제 로그에 패턴 폭에 잘린
        // `mousUser` 로 찍혀서 발견했다.
        SecurityContextHolder.getContext()
                .setAuthentication(new AnonymousAuthenticationToken(
                        "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        String[] seen = new String[1];

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/categories"), new MockHttpServletResponse(),
                (req, res) -> seen[0] = MDC.get(LogAttributes.USER_ID));

        assertEquals("anon", seen[0]);
    }

    @Test
    void Basic_계정은_이름을_그대로_쓴다() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("dev", null, List.of()));
        String[] seen = new String[1];

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/categories"), new MockHttpServletResponse(),
                (req, res) -> seen[0] = MDC.get(LogAttributes.USER_ID));

        // 앞자리를 자르지 않는다 — 이미 짧고, 자르면 어느 계정인지 오히려 알 수 없어진다.
        assertEquals("dev", seen[0]);
    }

    private static void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
