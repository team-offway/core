package com.offway.core.common.external;

import com.offway.core.common.logging.ExternalSystems;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/**
 * 외부 호출의 성패를 {@link ExternalApiHealth} 에 흘린다(#474).
 *
 * <p><b>어댑터를 하나도 안 건드린다.</b> 열다섯 어댑터가 {@code externalWebClient} 하나를 공유하므로
 * 필터 한 장이면 전부 계측된다 — {@code ExternalCallLoggingFilter} 가 이미 같은 자리에서 응답시간을
 * 재고 있다.
 *
 * <h2>실패로 세는 것</h2>
 *
 * <ul>
 *   <li><b>5xx</b> — 외부가 스스로 못 하겠다고 답한 것.
 *   <li><b>응답 자체가 없는 것</b> — 연결 실패·읽기 timeout.
 *   <li><b>취소</b> — 아래 참고.
 * </ul>
 *
 * <p><b>4xx 는 세지 않는다.</b> 그건 우리 요청이 잘못됐다는 뜻이지 외부가 죽은 것이 아니다. 없는 장소를
 * 물어 404 가 오는 것은 정상 동작이고, 그것으로 "관광 API 장애" 라 알리면 신호가 죽는다.
 *
 * <h2>취소를 반드시 잡아야 한다</h2>
 *
 * <p>어댑터들은 {@code .timeout(6초)} 를 하류에 건다. 그게 걸리면 상류가 <b>취소</b>되는데,
 * {@code doOnNext}·{@code doOnError} 는 취소 신호를 못 받는다 — 즉 <b>가장 느려서 죽은 호출이 관측에서
 * 통째로 빠진다.</b> 2026-09-06 장애가 정확히 그 모양이었다(TLS 악수 8~10초 vs 우리 상한 6초). 그것만
 * 놓치면 이 기능은 정작 필요한 순간에 침묵한다. {@code doFinally} 로 받는다.
 */
public final class ExternalHealthFilter {

    /** 하류 timeout 에 잘린 호출의 사유 — 외부가 우리 상한보다 느렸다는 뜻이다. */
    private static final String CAUSE_CANCELLED = "응답 없음(우리 timeout)";

    private ExternalHealthFilter() {
    }

    public static ExchangeFilterFunction create(ExternalApiHealth health) {
        return (request, next) -> {
            String system = ExternalSystems.label(request.url());
            // defer — 구독마다 플래그를 새로 만든다. 바깥에 두면 재시도·병렬 호출이 하나를 공유한다.
            return Mono.defer(() -> {
                AtomicBoolean settled = new AtomicBoolean();
                return next.exchange(request)
                        .doOnNext(response -> {
                            settled.set(true);
                            if (response.statusCode().is5xxServerError()) {
                                health.failed(system, "HTTP " + response.statusCode().value());
                            } else {
                                health.succeeded(system);
                            }
                        })
                        .doOnError(error -> {
                            settled.set(true);
                            health.failed(system, error.getClass().getSimpleName());
                        })
                        // 값도 예외도 없이 끝났다면 취소다 — 위 두 콜백이 못 받는 자리.
                        .doFinally(signal -> {
                            if (signal == SignalType.CANCEL && settled.compareAndSet(false, true)) {
                                health.failed(system, CAUSE_CANCELLED);
                            }
                        });
            });
        };
    }
}
