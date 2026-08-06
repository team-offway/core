package com.offway.core.common.logging;

import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

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
 * <p>실패한 호출도 시간을 적는다. 6초 timeout 으로 죽은 호출이 응답시간의 대부분일 때가 있는데,
 * 성공만 세면 그 시간이 로그에서 사라진다.
 */
public final class ExternalCallLoggingFilter {

    private ExternalCallLoggingFilter() {}

    public static ExchangeFilterFunction create() {
        return (request, next) -> {
            ExternalCallRecorder recorder = LogAttributes.currentRecorder();
            if (recorder == null) {
                return next.exchange(request);
            }
            String system = ExternalSystems.label(request.url());
            return next.exchange(request)
                    .elapsed()
                    .doOnNext(timed -> recorder.record(system, timed.getT1()))
                    .map(timed -> timed.getT2())
                    .doOnError(error -> recorder.record(system, 0L));
        };
    }
}
