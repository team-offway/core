package com.offway.core.common.external;

import java.util.function.Supplier;

/**
 * 지금 실행 중인 작업의 {@link Caller} 를 들고 있는 스레드 지역 홀더(#285).
 *
 * <p><b>왜 파라미터가 아니라 홀더인가.</b> {@code TourApiClientImpl.candidates()} 는 코스 생성도 부르고
 * 배치도 부른다 — <b>클라이언트는 자기가 왜 불렸는지 모른다.</b> 포트 시그니처에 주체를 더하면 11 개
 * 외부 클라이언트가 전부 관측 관심사에 오염되고, 도메인이 그것을 들고 다니게 된다.
 *
 * <p><b>스레드를 넘길 때는 {@link #wrap(Runnable)} 로 감싼다.</b> 스레드 지역이라 풀에 던지는 순간
 * 맥락이 사라진다. 감싸는 것을 빠뜨리면 그 호출은 {@link Caller#UNKNOWN} 으로 들어가 미상 비중이 커지므로,
 * 조용히 틀리는 대신 눈에 보인다.
 */
public final class CallerContext {

    private static final ThreadLocal<Caller> CURRENT = new ThreadLocal<>();

    private CallerContext() {
    }

    /** 지금 맥락의 주체. 심어진 적이 없으면 {@link Caller#UNKNOWN}. */
    public static Caller current() {
        Caller caller = CURRENT.get();
        return caller == null ? Caller.UNKNOWN : caller;
    }

    /** 주체를 심고 작업을 돌린다. 끝나면 <b>직전 값으로 되돌린다</b> — 중첩해도 바깥 맥락이 살아남는다. */
    public static void run(Caller caller, Runnable body) {
        call(caller, () -> {
            body.run();
            return null;
        });
    }

    /** 값을 돌려주는 {@link #run(Caller, Runnable)}. */
    public static <T> T call(Caller caller, Supplier<T> body) {
        Caller previous = CURRENT.get();
        set(caller);
        try {
            return body.get();
        } finally {
            restore(previous);
        }
    }

    /**
     * 지금 맥락을 <b>붙여 둔</b> 작업으로 감싼다. 다른 스레드에서 돌아도 주체가 따라간다.
     *
     * <p>감싸는 시점의 주체를 잡는다 — 실행 시점이 아니다. 풀에 들어간 뒤에는 제출한 쪽이 이미 다른 일을
     * 하고 있을 수 있다.
     */
    public static Runnable wrap(Runnable task) {
        Caller captured = current();
        return () -> run(captured, task);
    }

    /** 인터셉터처럼 진입·이탈 시점이 갈린 곳에서 쓴다. 짝이 되는 {@link #clear()} 를 반드시 부른다. */
    static void set(Caller caller) {
        CURRENT.set(caller);
    }

    /** 스레드가 풀로 돌아가므로 반드시 비운다 — 안 비우면 다음 요청이 남의 주체를 물려받는다. */
    static void clear() {
        CURRENT.remove();
    }

    private static void restore(Caller previous) {
        if (previous == null) {
            clear();
            return;
        }
        set(previous);
    }
}
