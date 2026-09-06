package com.offway.core.common.external;

import com.offway.core.common.logging.ExternalSystems;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.web.reactive.function.client.ClientResponse;
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
 *
 * <h2>성공은 본문까지 읽힌 뒤에 센다</h2>
 *
 * <p>{@code exchange} 는 <b>헤더만 받아도 완료된다.</b> 거기서 성공을 기록하면, 헤더는 왔는데 본문이
 * 느려 하류 timeout 에 잘린 호출이 <b>성공으로 남는다</b> — 취소는 이미 "판정 끝" 이라 무시되기 때문이다.
 * 죽어 가는 게이트웨이가 헤더만 먼저 흘리는 모양이 정확히 그것이라, 이 기능이 필요한 순간에 또 침묵한다.
 *
 * <p>그래서 성공 판정을 <b>본문 스트림의 완료</b>로 옮긴다. 5xx 는 본문을 기다릴 이유가 없어 헤더에서
 * 바로 실패로 센다. 본문을 안 읽는 호출({@code toBodilessEntity})도 본문을 drain 하고 완료 신호를
 * 주므로 같은 경로를 탄다.
 */
public final class ExternalHealthFilter {

    /** 하류 timeout 에 잘린 호출의 사유 — 외부가 우리 상한보다 느렸다는 뜻이다. */
    private static final String CAUSE_CANCELLED = "응답 없음(우리 timeout)";

    private ExternalHealthFilter() {
    }

    /**
     * 본문 스트림의 끝을 성패로 옮긴다 — 헤더만으로 판정하지 않으려는 것.
     *
     * <p>5xx 는 여기 오기 전에 갈린다. 외부가 스스로 못 하겠다고 답한 것이라 본문을 기다릴 이유가 없다.
     */
    private static ClientResponse observeBody(
            ClientResponse response, ExternalApiHealth health, String system, AtomicBoolean settled) {
        if (response.statusCode().is5xxServerError()) {
            settle(settled, () -> health.failed(system, "HTTP " + response.statusCode().value()));
            return response;
        }
        return response.mutate()
                .body(body -> body
                        .doOnComplete(() -> settle(settled, () -> health.succeeded(system)))
                        .doOnCancel(() -> settle(settled, () -> health.failed(system, CAUSE_CANCELLED)))
                        .doOnError(error ->
                                settle(settled, () -> health.failed(system, error.getClass().getSimpleName()))))
                .build();
    }

    /** 한 호출은 한 번만 판정된다 — 헤더·본문·취소가 서로 덮어쓰지 않게. */
    private static void settle(AtomicBoolean settled, Runnable record) {
        if (settled.compareAndSet(false, true)) {
            record.run();
        }
    }

    public static ExchangeFilterFunction create(ExternalApiHealth health) {
        return (request, next) -> {
            String system = ExternalSystems.label(request.url());
            // defer — 구독마다 플래그를 새로 만든다. 바깥에 두면 재시도·병렬 호출이 하나를 공유한다.
            return Mono.defer(() -> {
                AtomicBoolean settled = new AtomicBoolean();
                return next.exchange(request)
                        .map(response -> observeBody(response, health, system, settled))
                        .doOnError(error -> settle(settled, () -> health.failed(system, error.getClass().getSimpleName())))
                        // 값도 예외도 없이 끝났다면 취소다 — 위 콜백들이 못 받는 자리.
                        // 본문 단계의 취소는 아래 observeBody 가 따로 받는다.
                        .doFinally(signal -> {
                            if (signal == SignalType.CANCEL) {
                                settle(settled, () -> health.failed(system, CAUSE_CANCELLED));
                            }
                        });
            });
        };
    }
}
