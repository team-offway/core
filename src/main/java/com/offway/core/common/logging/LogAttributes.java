package com.offway.core.common.logging;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/** 요청 하나에 걸쳐 나르는 로그 재료의 속성 키. */
public final class LogAttributes {

    /**
     * MDC 키 — 요청 하나에 붙는 짧은 추적 id. 로그 패턴({@code logging.pattern.level})이 이 값을 찍는다.
     *
     * <p>요청 줄에만 두지 않고 MDC 에 넣는 이유: 추적의 값은 <b>그 요청 중에 나온 다른 로그</b>(외부 호출
     * 실패 warn, 예외 스택)를 같은 id 로 묶는 데 있다. 요청 줄에만 있으면 정작 원인 줄이 어느 요청 것인지
     * 모른다 — 스레드 이름으로 역추적하던 그 문제로 되돌아간다.
     */
    public static final String TRACE_ID = "traceId";

    /**
     * MDC 키 — 요청을 보낸 주체.
     *
     * <p>지금은 임시 Basic 계정 이름 하나뿐이라(#122) 값이 늘 같지만, OAuth 로 실사용자가 들어오면 이 자리가
     * 사용자 id 가 된다. 그때 로그 형식을 다시 바꾸지 않으려고 자리를 미리 잡아둔다.
     */
    public static final String USER_ID = "userId";

    public static final String EXTERNAL_CALLS = "offway.log.externalCalls";
    public static final String REQUEST_SUMMARY = "offway.log.requestSummary";
    public static final String RESPONSE_SUMMARY = "offway.log.responseSummary";

    private LogAttributes() {}

    /**
     * 현재 요청의 외부 호출 수집기. 요청 밖(스케줄러 워밍 등)이면 {@code null}.
     *
     * <p>워밍은 요청이 아니므로 귀속시킬 대상이 없다 — 조용히 건너뛴다.
     */
    public static ExternalCallRecorder currentRecorder() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        Object recorder = attributes.getAttribute(EXTERNAL_CALLS, RequestAttributes.SCOPE_REQUEST);
        return recorder instanceof ExternalCallRecorder found ? found : null;
    }
}
