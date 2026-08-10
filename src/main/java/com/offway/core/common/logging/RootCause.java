package com.offway.core.common.logging;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * 예외 체인의 맨 끝을 로그용 한 줄로 — 껍데기가 아니라 <b>실제 사유</b>를 남긴다.
 *
 * <p><b>왜 필요한가.</b> 외부 호출 실패를 {@code e.getClass().getSimpleName()} 으로 찍으면 늘 같은 껍데기만
 * 나온다 — WebClient 는 {@code ReactiveException} 으로, 우리 어댑터는 {@code TourApiException} 으로 감싸기
 * 때문이다. 그러면 timeout 인지 429 인지 파싱 오류인지 구분이 안 돼, 로그를 보고도 원인을 못 찾는다.
 *
 * <p>실제로 운영 로그가 {@code cause=ReactiveException} 만 반복해 찍어 별도로 실호출을 떠서야 원인을 알았다(#184).
 *
 * <p>스택을 통째로 남기지 않는 이유는 반대쪽 실패에서 나왔다 — 외부 실패는 예상 범위 안의 사건인데 Reactor
 * 체크포인트까지 붙으면 한 건이 60줄이 넘고, 89개 지역이면 로그가 수천 줄이 된다(#191). 사유 한 줄이면 충분하다.
 */
public final class RootCause {

    /**
     * 메시지 표시 상한. 넘으면 잘라내고 표식을 붙인다.
     *
     * <p>외부 예외 메시지는 요청 URL·응답 본문 일부를 통째로 담기도 한다. 로그 한 줄이 화면을 넘기면
     * 그 줄만 못 읽는 게 아니라 앞뒤 줄까지 훑기 어려워진다.
     */
    private static final int MAX_MESSAGE_LENGTH = 200;

    private static final String TRUNCATED = "…";

    private static final String SEPARATOR = ": ";

    private static final String UNKNOWN = "unknown";

    private RootCause() {
    }

    /**
     * 원인 체인의 마지막 예외를 {@code 클래스명: 사유} 로.
     *
     * <p>메시지까지 담는다 — timeout 은 몇 초였는지, 파싱 실패는 어느 필드인지가 거기 들어 있다. 클래스명만으로는
     * {@code TimeoutException} 하나에 여러 원인이 뭉뚱그려진다.
     *
     * <p><b>메시지는 그대로 싣지 않는다.</b> {@code WebClientResponseException} 메시지에는 요청 URL 이 통째로
     * 들어올 수 있고 우리 외부 호출은 {@code serviceKey} 를 쿼리에 싣는다 — 비밀값을 가리고, 제어문자를
     * 걷어내고(개행 하나가 로그를 여러 줄로 쪼갠다), 길이를 자른다.
     *
     * @param error 감싸인 예외. null 이면 {@code "unknown"}
     */
    public static String of(Throwable error) {
        if (error == null) {
            return UNKNOWN;
        }
        Throwable cause = deepestCause(error);
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        return cause.getClass().getSimpleName() + SEPARATOR + safeMessage(message);
    }

    /**
     * 체인의 맨 끝. <b>이미 본 예외를 만나면 멈춘다.</b>
     *
     * <p>자기 자신만 확인하면 부족하다 — {@code A → B → A} 처럼 서로를 cause 로 갖는 체인은 자기 참조가
     * 아니어서 그 검사를 통과하고, 로그를 찍다가 무한 루프에 빠진다. 실패를 기록하려던 코드가 프로세스를
     * 잡아먹는 셈이라, 동일성 기준 방문 집합으로 끊는다.
     */
    private static Throwable deepestCause(Throwable error) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable cause = error;
        seen.add(cause);
        while (cause.getCause() != null && seen.add(cause.getCause())) {
            cause = cause.getCause();
        }
        return cause;
    }

    /** 비밀값 마스킹 → 제어문자 제거 → 길이 제한. 이 순서를 지켜야 마스킹이 개행에 쪼개지지 않는다. */
    private static String safeMessage(String message) {
        String masked = SensitiveParams.maskSecretsInText(message);
        String oneLine = SensitiveParams.stripControlChars(masked).trim();
        return oneLine.length() <= MAX_MESSAGE_LENGTH
                ? oneLine
                : oneLine.substring(0, MAX_MESSAGE_LENGTH) + TRUNCATED;
    }
}
