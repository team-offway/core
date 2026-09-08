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
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;
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

    /**
     * <b>헤더만 오고 본문이 안 오는 경우</b>도 실패다(CodeRabbit #477 리뷰).
     *
     * <p>{@code exchange} 는 헤더만 받아도 완료된다. 거기서 성공을 기록하면, 본문이 느려 하류 timeout 에
     * 잘린 호출이 <b>성공으로 남는다</b> — 죽어 가는 게이트웨이가 헤더만 먼저 흘리는 모양이 그것이라,
     * 이 기능이 정작 필요한 순간에 또 침묵한다.
     */
    @Test
    void 헤더는_왔는데_본문이_안_와_취소되면_실패로_센다() {
        RecordingNotifier notifier = new RecordingNotifier();
        ExternalApiHealth health = new ExternalApiHealth(notifier);
        ExchangeFilterFunction filter = ExternalHealthFilter.create(health);

        // 헤더는 즉시, 본문은 영영 안 온다. 어댑터의 하류 timeout 이 본문 읽기에서 걸린다.
        ExchangeFunction headerThenStall = req -> Mono.just(
                ClientResponse.create(HttpStatus.OK).body(Flux.<DataBuffer>never()).build());

        for (int i = 0; i < 3; i++) {
            assertThrows(RuntimeException.class,
                    () -> filter.filter(request(), headerThenStall)
                            .flatMap(response -> response.bodyToMono(String.class))
                            .timeout(Duration.ofMillis(50))
                            .block());
        }

        assertFalse(health.isHealthy("train"), "본문 timeout 이 성공으로 남으면 죽어 가는 게이트웨이를 놓친다");
        assertEquals(1, notifier.sent.size());
    }

    /**
     * 성공 경로를 <b>회복 알림으로</b> 확인한다.
     *
     * <p>"살아 있다" 만 단언하면 증명이 안 된다 — 아직 한 번도 안 불린 시스템도 살아 있는 것으로 보기
     * 때문에, 성공이 아예 기록되지 않아도 그 단언은 통과한다. 먼저 죽여 두고 회복 알림이 오는지를 본다.
     */
    @Test
    void 본문까지_읽고_끝나면_성공으로_세고_회복을_알린다() {
        RecordingNotifier notifier = new RecordingNotifier();
        ExternalApiHealth health = new ExternalApiHealth(notifier);
        ExchangeFilterFunction filter = ExternalHealthFilter.create(health);

        ExchangeFunction broken = req -> Mono.error(new IOException("connection reset"));
        for (int i = 0; i < 3; i++) {
            assertThrows(RuntimeException.class, () -> filter.filter(request(), broken).block());
        }

        // 실제 어댑터 모양 — 헤더를 받고 본문까지 읽는다. 회복은 연속 성공을 요구한다(#482).
        for (int i = 0; i < 2; i++) {
            filter.filter(request(), req -> Mono.just(ClientResponse.create(HttpStatus.OK).body("{}").build()))
                    .flatMap(response -> response.bodyToMono(String.class))
                    .block();
        }

        assertTrue(health.isHealthy("train"));
        assertEquals(2, notifier.sent.size());
        assertTrue(notifier.sent.getLast().contains("회복"));
    }

    /**
     * 본문을 안 읽는 호출도 같은 경로를 탄다 — {@code toBodilessEntity} 는 본문을 drain 하고 완료 신호를
     * 준다. 이게 아니면 응답 본문을 안 쓰는 어댑터의 성공이 영영 기록되지 않아 회복 알림이 안 온다.
     */
    @Test
    void 본문을_안_읽는_호출도_성공으로_센다() {
        RecordingNotifier notifier = new RecordingNotifier();
        ExternalApiHealth health = new ExternalApiHealth(notifier);
        ExchangeFilterFunction filter = ExternalHealthFilter.create(health);

        ExchangeFunction broken = req -> Mono.error(new IOException("connection reset"));
        for (int i = 0; i < 3; i++) {
            assertThrows(RuntimeException.class, () -> filter.filter(request(), broken).block());
        }

        for (int i = 0; i < 2; i++) {
            filter.filter(request(), req -> Mono.just(ClientResponse.create(HttpStatus.OK).body("{}").build()))
                    .flatMap(ClientResponse::toBodilessEntity)
                    .block();
        }

        assertTrue(health.isHealthy("train"));
        assertTrue(notifier.sent.getLast().contains("회복"));
    }

    /**
     * <b>프로브 요청은 여기서 관측하지 않는다</b>(#479).
     *
     * <p>프로브는 HTTP 200 안에 실린 {@code resultCode} 까지 보는데, 필터는 그 200 만 보고 성공으로
     * 적는다. 둘 다 적으면 카운터가 <b>성공 → 실패 1</b> 을 반복해 연속 실패가 쌓이지 않고, 장애
     * 확정선에 영영 닿지 못한다. 그래서 필터가 비켜서고 스케줄러가 단독으로 적는다.
     */
    @Test
    void 프로브_요청은_관측하지_않는다() {
        RecordingNotifier notifier = new RecordingNotifier();
        ExternalApiHealth health = new ExternalApiHealth(notifier);
        ExchangeFilterFunction filter = ExternalHealthFilter.create(health);
        ClientRequest probeRequest = ClientRequest.create(HttpMethod.GET, TRAIN)
                .attribute(ExternalHealthFilter.SKIP_ATTRIBUTE, true)
                .build();

        // 200 이 다섯 번 와도 성공으로 세지 않는다 — 그 판정은 스케줄러 몫이다.
        for (int i = 0; i < 5; i++) {
            filter.filter(probeRequest, responding(HttpStatus.OK)).block();
        }
        // 5xx 도 마찬가지다. 기록자가 둘이 되면 안 된다.
        for (int i = 0; i < 5; i++) {
            filter.filter(probeRequest, responding(HttpStatus.BAD_GATEWAY)).block();
        }

        assertEquals(List.of(), notifier.sent, "필터가 프로브까지 적으면 스케줄러의 판정과 서로 덮어쓴다");
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

    /**
     * 키가 막힌 것은 4xx 라도 장애다(#489). 2026-09-07 운영 키가 403 을 받는 동안 화면에서 장소 소개·
     * 사진·공휴일이 통째로 사라졌는데 알림이 한 줄도 안 갔다.
     */
    @Test
    void 키가_막히면_4xx라도_장애로_센다() {
        RecordingNotifier notifier = new RecordingNotifier();
        ExternalApiHealth health = new ExternalApiHealth(notifier);
        ExchangeFilterFunction filter = ExternalHealthFilter.create(health);

        for (int i = 0; i < 3; i++) {
            filter.filter(request(), responding(HttpStatus.FORBIDDEN)).block();
        }

        assertFalse(health.isHealthy("train"), "다음 요청도 똑같이 막힌다 — 사용자에겐 죽은 것과 같다");
        assertTrue(notifier.sent.getFirst().contains("키가 막혔습니다"), "할 일이 다르니 사유를 갈라 적는다");
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
