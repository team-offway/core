package com.offway.core.common.exception;

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

    private RootCause() {
    }

    /**
     * 원인 체인의 마지막 예외를 {@code 클래스명: 메시지} 로.
     *
     * <p>메시지까지 담는다 — timeout 은 몇 초였는지, 파싱 실패는 어느 필드인지가 거기 들어 있다. 클래스명만으로는
     * {@code TimeoutException} 하나에 여러 원인이 뭉뚱그려진다.
     *
     * @param error 감싸인 예외. null 이면 {@code "unknown"}
     */
    public static String of(Throwable error) {
        if (error == null) {
            return "unknown";
        }
        Throwable cause = error;
        // 자기 자신을 cause 로 갖는 예외가 있다 — 그대로 두면 무한 루프다.
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null
                ? cause.getClass().getSimpleName()
                : cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
