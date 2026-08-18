package com.offway.core.common.external;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 요청에서 주체를 뽑는 규칙(#285).
 *
 * <p><b>여기가 이 작업의 키 공간 상한을 지킨다.</b> 경로를 그대로 쓰면 코스 id 마다 다른 주체가 생겨
 * 행이 무한히 늘고, 알림 내역이 한 줄짜리 주체로 가득 찬다.
 */
class CallerAttributionInterceptorTest {

    private final CallerAttributionInterceptor interceptor = new CallerAttributionInterceptor();

    @Test
    void 경로가_아니라_매핑_패턴을_쓴다() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/courses/123");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/courses/{id}");

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        try {
            assertEquals(Caller.of("GET /api/v1/courses/{id}"), CallerContext.current());
        } finally {
            CallerContext.clear();
        }
    }

    @Test
    void 메서드가_다르면_다른_주체다() {
        MockHttpServletRequest post = new MockHttpServletRequest("POST", "/api/v1/courses");
        post.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/courses");

        interceptor.preHandle(post, new MockHttpServletResponse(), new Object());

        try {
            assertEquals(Caller.of("POST /api/v1/courses"), CallerContext.current());
        } finally {
            CallerContext.clear();
        }
    }

    /** 패턴이 없는 요청(정적 리소스 등)은 미상으로 둔다 — 경로로 대체하면 상한이 없어진다. */
    @Test
    void 패턴이_없으면_미상이다() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/favicon.ico");

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        try {
            assertEquals(Caller.UNKNOWN, CallerContext.current());
        } finally {
            CallerContext.clear();
        }
    }

    /**
     * 스레드가 톰캣 풀로 돌아가므로 반드시 비운다.
     *
     * <p>핸들러가 예외로 끝나도 불리는 자리라 여기서 비운다 — 안 비우면 다음 요청이 남의 주체를 물려받아
     * <b>조용히 틀린 값</b>이 된다.
     */
    @Test
    void 요청이_끝나면_비운다() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/quotas");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/quotas");
        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        interceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(),
                new IllegalStateException("핸들러가 터져도 비운다"));

        assertEquals(Caller.UNKNOWN, CallerContext.current());
    }
}
