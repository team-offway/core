package com.offway.core.common.external;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * HTTP 요청이 일으킨 외부 호출에 <b>엔드포인트</b>를 주체로 심는다(#285).
 *
 * <p><b>필터가 아니라 인터셉터인 이유.</b> 필터는 핸들러 매핑 <b>전</b>이라 어떤 엔드포인트로 갈지 모른다.
 * 그 자리에서 알 수 있는 것은 경로({@code /api/v1/courses/123})뿐인데, 경로를 주체로 쓰면 id 마다 다른
 * 주체가 생겨 키 공간에 상한이 없어진다. 인터셉터는 매핑 뒤라 <b>패턴</b>({@code /api/v1/courses/{id}})을
 * 꺼낼 수 있고, 그래야 주체가 엔드포인트 수만큼으로 유한하다.
 */
public class CallerAttributionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        CallerContext.set(callerOf(request));
        return true;
    }

    /**
     * 스레드가 톰캣 풀로 돌아가므로 반드시 비운다.
     *
     * <p>안 비우면 다음 요청이 남의 주체를 물려받아 <b>조용히 틀린 값</b>이 된다 — 미상보다 나쁘다.
     * 핸들러가 예외로 끝나도 불리는 자리라 여기서 비운다.
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception ex) {
        CallerContext.clear();
    }

    private Caller callerOf(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern == null) {
            return Caller.UNKNOWN;
        }
        return Caller.request(request.getMethod(), pattern.toString());
    }
}
