package com.offway.core.common.logging;

import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

/**
 * 외부 호출 하나하나의 소요시간을 요청 스코프 수집기에 누적한다.
 *
 * <p><b>수집기 참조를 구독 시점에 붙잡는다.</b> {@code filter(...)} 는 어댑터가 {@code .block()} 할 때
 * 서블릿 스레드에서 불리므로 여기서는 {@code RequestContextHolder} 가 보인다. 반면 완료 콜백은 리액터
 * 스레드에서 돌아 보이지 않는다 — 그래서 완료 시점에 다시 찾지 않고, {@code filter(...)} 진입 시 붙잡아 둔
 * 지역 변수({@code recorder})에 적는다. 완료 콜백 안에서 {@link LogAttributes#currentRecorder()} 를
 * 다시 부르면 항상 {@code null} 이 나와 계측이 통째로 죽는다.
 *
 * <p>요청 밖(캐시 워밍 스케줄러 등)에서 같은 {@code WebClient} 를 쓰는 경우가 정상적으로 있다. 그때는
 * 수집기가 없으므로 조용히 계측을 건너뛰고 호출은 그대로 통과시킨다 — 예외를 던지거나 로그를 남기지 않는다.
 *
 * <p><b>취소(timeout)도 종료 신호로 잡는다.</b> 모든 어댑터는 {@code .timeout(...)} 을 이 필터의
 * <i>하류</i>(retrieve 이후)에 건다. 리액터의 {@code timeout()} 은 시간이 지나면 상류를 <b>취소</b>한다 —
 * {@code onNext}·{@code onError} 어느 쪽도 불리지 않는다. 그래서 {@code doOnNext}/{@code doOnError} 로
 * 짜면 timeout 으로 죽은 호출은 시간이 통째로 로그에서 사라진다(정작 이 기능이 잡으려던 느린 경로가 빠진다).
 * {@code doFinally} 는 완료·에러·취소 세 신호 모두에서 불리므로 여기로 기록한다.
 *
 * <p>측정 시작은 {@link Mono#defer(java.util.function.Supplier)} 안에서 잡는다 — {@code filter(...)} 는
 * 체인을 조립하는 시점에 한 번 불리고, 실제 구독(그리고 실제 호출)은 그보다 나중일 수 있다. {@code defer}
 * 밖에서 시간을 재면 조립~구독 사이의 대기시간까지 호출 시간으로 잡힌다.
 *
 * <p><b>한계 — 측정 구간은 구독부터 응답 헤더까지다.</b> {@code next.exchange(...)} 는 응답 헤더가 도착하는
 * 즉시 발행하고, 본문은 그 뒤에 별도로 소비된다. 즉 여기 기록되는 시간에는 <b>본문 수신 시간이 포함되지
 * 않는다.</b> 응답이 클수록(현재 {@code maxInMemorySize} 2MB 한도 안에서) 실제보다 빨라 보일 수 있다는
 * 뜻이다. 본문까지 재려면 응답을 감싸는 디코레이터가 필요한데, 지금 목적(느린 외부 호출을 눈에 띄게 하는 것)
 * 대비 들어가는 기계장치가 과해 여기서는 들이지 않는다.
 */
public final class ExternalCallLoggingFilter {

    private static final long NANOS_PER_MILLI = 1_000_000L;

    private ExternalCallLoggingFilter() {}

    public static ExchangeFilterFunction create() {
        return (request, next) -> {
            ExternalCallRecorder recorder = LogAttributes.currentRecorder();
            if (recorder == null) {
                return next.exchange(request);
            }
            String system = ExternalSystems.label(request.url());
            return Mono.defer(() -> {
                long startedAt = System.nanoTime();
                return next.exchange(request).doFinally(signalType -> recorder.record(system, elapsedMillis(startedAt)));
            });
        };
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / NANOS_PER_MILLI;
    }
}
