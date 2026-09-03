package com.offway.core.common.external;

import java.util.Optional;
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

    /**
     * 이 요청이 태운 외부 호출(#421).
     *
     * <p>주체와 <b>같은 자리</b>에 둔다. 따로 두면 스레드를 넘길 때 한쪽만 따라가는 일이 생기고,
     * 그때 알림은 조용히 작아진다 — 코스 생성이 가장 많이 태우는 경로가 정확히 그 팬아웃이다.
     */
    private static final ThreadLocal<RequestUsage> USAGE = new ThreadLocal<>();

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

    /** 이 요청의 집계. 열려 있지 않으면 빈 값이다 — 배치처럼 대상이 아닌 경로가 그렇다. */
    public static Optional<RequestUsage> usage() {
        return Optional.ofNullable(USAGE.get());
    }

    /**
     * 이 요청의 집계를 연다. 짝이 되는 {@link #clear()} 를 반드시 부른다.
     *
     * <p>이미 열려 있으면 <b>그것을 그대로 준다.</b> 새로 만들면 바깥에서 세던 숫자가 끊긴다.
     */
    public static RequestUsage beginUsage() {
        RequestUsage opened = USAGE.get();
        if (opened != null) {
            return opened;
        }
        RequestUsage fresh = new RequestUsage();
        USAGE.set(fresh);
        return fresh;
    }

    /**
     * 지금 맥락을 <b>붙여 둔</b> 작업으로 감싼다. 다른 스레드에서 돌아도 주체와 집계가 따라간다.
     *
     * <p>감싸는 시점의 값을 잡는다 — 실행 시점이 아니다. 풀에 들어간 뒤에는 제출한 쪽이 이미 다른 일을
     * 하고 있을 수 있다.
     *
     * <p><b>집계는 같은 참조를 넘긴다.</b> 값을 복사하면 병렬로 나간 호출이 통째로 안 세어지고,
     * 코스 생성이 가장 많이 태우는 경로가 정확히 그 팬아웃이라 알림이 늘 실제보다 작아진다.
     */
    public static Runnable wrap(Runnable task) {
        Caller captured = current();
        RequestUsage capturedUsage = USAGE.get();
        return () -> {
            RequestUsage previous = USAGE.get();
            setUsage(capturedUsage);
            try {
                run(captured, task);
            } finally {
                setUsage(previous);
            }
        };
    }

    /** 인터셉터처럼 진입·이탈 시점이 갈린 곳에서 쓴다. 짝이 되는 {@link #clear()} 를 반드시 부른다. */
    static void set(Caller caller) {
        CURRENT.set(caller);
    }

    /**
     * 스레드가 풀로 돌아가므로 반드시 비운다 — 안 비우면 다음 요청이 남의 주체와 <b>남의 숫자</b>를
     * 물려받는다. 미상보다 나쁘다.
     */
    static void clear() {
        CURRENT.remove();
        USAGE.remove();
    }

    /**
     * null 이면 <b>지운다.</b> {@code set(null)} 로 두면 값이 없는데 엔트리는 남아, 스레드가 풀로
     * 돌아간 뒤에도 이 스레드에 자리가 붙어 있다.
     */
    private static void setUsage(RequestUsage usage) {
        if (usage == null) {
            USAGE.remove();
            return;
        }
        USAGE.set(usage);
    }

    private static void restore(Caller previous) {
        if (previous == null) {
            clear();
            return;
        }
        set(previous);
    }
}
