package com.offway.core.common.logging;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/** 요청 하나에 걸쳐 나르는 로그 재료의 속성 키. */
public final class LogAttributes {

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
