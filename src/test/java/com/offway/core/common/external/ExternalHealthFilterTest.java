package com.offway.core.common.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.notification.Notifier;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/**
 * {@link ExternalHealthFilter} 단위 테스트.
 *
 * <p>가장 중요한 것은 <b>취소 경로</b>다. 어댑터가 하류에 {@code .timeout(6초)} 를 걸어 두었으므로,
 * 외부가 그보다 느리면 응답도 예외도 없이 취소만 일어난다 — 2026-09-06 장애가 그 모양이었다.
 * 그 경로를 못 잡으면 이 기능은 정작 필요할 때 침묵한다.
 */
class ExternalHealthFilterTest {

    private static final URI TRAIN = URI.create(
            "https://apis.data.go.kr/1613000/TrainInfo/GetStrtpntAlocFndTrainInfo");

    private static final class RecordingNotifier implements Notifier {

        private final List<String> sent = new ArrayList<>();

        @Override
        public void send(String message) {
            sent.add(message);
        }
    }

    private static ClientRequest request() {
        return ClientRequest.create(HttpMethod.GET, TRAIN).build();
    }

    private static ExchangeFunction responding(HttpStatus status) {
        return req -> Mono.just(ClientResponse.create(status).build());
    }

    @Test
    void 응답이_우리_timeout보다_느려_취소되면_실패로_센다() {
        RecordingNotifier notifier = new RecordingNotifier();
        ExternalApiHealth health = new ExternalApiHealth(notifier);
        ExchangeFilterFunction filter = ExternalHealthFilter.create(health);

        // 실제 어댑터 모양 — 느린 상류에 짧은 하류 timeout. 상류는 취소되고 값도 예외도 안 온다.
        ExchangeFunction stalled = req -> Mono.delay(Duration.ofSeconds(5))
                .then(Mono.just(ClientResponse.create(HttpStatus.OK).build()));

        for (int i = 0; i < 3; i++) {
            assertThrows(RuntimeException.class,
                    () -> filter.filter(request(), stalled).timeout(Duration.ofMillis(50)).block());
        }

        assertFalse(health.isHealthy("train"), "취소된 호출이 관측에서 빠지면 가장 느린 장애를 놓친다");
        assertEquals(1, notifier.sent.size());
    }

    @Test
    void 응답이_5xx면_실패로_센다() {
        RecordingNotifier notifier = new RecordingNotifier();
        ExternalApiHealth health = new ExternalApiHealth(notifier);
        ExchangeFilterFunction filter = ExternalHealthFilter.create(health);

        for (int i = 0; i < 3; i++) {
            filter.filter(request(), responding(HttpStatus.BAD_GATEWAY)).block();
        }

        assertFalse(health.isHealthy("train"));
        assertTrue(notifier.sent.getFirst().contains("502"));
    }

    @Test
    void 응답이_4xx면_실패로_세지_않는다() {
        RecordingNotifier notifier = new RecordingNotifier();
        ExternalApiHealth health = new ExternalApiHealth(notifier);
        ExchangeFilterFunction filter = ExternalHealthFilter.create(health);

        for (int i = 0; i < 5; i++) {
            filter.filter(request(), responding(HttpStatus.NOT_FOUND)).block();
        }

        assertTrue(health.isHealthy("train"), "없는 것을 물어 404 가 오는 건 정상 동작이지 외부 장애가 아니다");
        assertEquals(List.of(), notifier.sent);
    }

    @Test
    void 연결이_끊기면_실패로_센다() {
        RecordingNotifier notifier = new RecordingNotifier();
        ExternalApiHealth health = new ExternalApiHealth(notifier);
        ExchangeFilterFunction filter = ExternalHealthFilter.create(health);

        ExchangeFunction broken = req -> Mono.error(new IOException("connection reset"));

        for (int i = 0; i < 3; i++) {
            assertThrows(RuntimeException.class, () -> filter.filter(request(), broken).block());
        }

        assertFalse(health.isHealthy("train"));
    }

    @Test
    void 시스템은_호스트가_아니라_경로로_가른다() {
        RecordingNotifier notifier = new RecordingNotifier();
        ExternalApiHealth health = new ExternalApiHealth(notifier);
        ExchangeFilterFunction filter = ExternalHealthFilter.create(health);

        // 같은 호스트지만 다른 기관 — 열차가 죽어도 관광은 멀쩡한 것으로 남아야 한다.
        ClientRequest tour = ClientRequest.create(
                HttpMethod.GET,
                URI.create("https://apis.data.go.kr/B551011/KorService2/detailCommon2")).build();

        for (int i = 0; i < 3; i++) {
            filter.filter(request(), responding(HttpStatus.BAD_GATEWAY)).block();
        }
        filter.filter(tour, responding(HttpStatus.OK)).block();

        assertFalse(health.isHealthy("train"));
        assertTrue(health.isHealthy("tour"));
    }
}
