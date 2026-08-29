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
     * MDC 키 — 요청을 보낸 주체(#41).
     *
     * <p><b>요청이 시작될 때 넣는다.</b> 예전에는 응답을 다 쓴 뒤 {@code finally} 에서 넣어, 정작 그 요청
     * 중에 난 로그(외부 호출 실패 warn, 예외 스택)에는 붙지 않았다 — 요청 줄 한 줄에만 있는 값이라
     * MDC 에 둘 이유가 없던 셈이다. traceId 를 MDC 에 둔 이유가 여기에도 그대로 적용된다.
     *
     * <p>값은 <b>짧은 앞자리</b>다. 사용자 식별자가 UUID(36자)라 그대로 매 줄에 박으면 정작 읽어야 할
     * 경로·메시지가 밀려난다. 전문이 필요한 자리는 로그인 성공 줄 하나뿐이고, 거기서는 전문을 남긴다.
     *
     * <p>Bearer 가 아닌 요청(Basic·비인증)도 이 자리를 쓴다 — 무엇으로 들어왔는지가 곧 신원이다.
     */
    public static final String USER_ID = "userId";

    /**
     * 요청 속성 키 — Bearer 토큰을 <b>왜</b> 거절했는지(만료·서명 불일치·형식 오류).
     *
     * <p>토큰을 푸는 곳과 401 을 쓰는 곳이 다르다. 푸는 쪽은 사유를 알지만 어느 경로·누구인지 모르고,
     * 쓰는 쪽은 그 반대다. 사유를 요청에 실어 넘겨 <b>401 한 줄에 둘을 합친다</b> — 나눠 찍으면 두 줄이
     * 되는데, 401 경로에는 추적 id 조차 없어(보안 필터가 요청 로깅 필터보다 앞이다) 다시 묶을 수단이 없다.
     */
    public static final String TOKEN_REJECTION = "offway.log.tokenRejection";

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
