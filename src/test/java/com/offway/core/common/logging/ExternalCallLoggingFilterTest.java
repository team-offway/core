package com.offway.core.common.logging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/**
 * {@link ExternalCallLoggingFilter} 단위 테스트 — 특히 어댑터의 {@code .timeout(...)} 이 하류에서 상류를
 * <b>취소</b>하는 경로가 기록되는지를 확인한다. 이 경로가 최종 리뷰에서 지적된 지점이다: {@code doOnNext}·
 * {@code doOnError} 조합은 취소 신호를 못 받아 timeout 으로 죽은 느린 호출이 로그에서 통째로 사라졌다.
 */
class ExternalCallLoggingFilterTest {

    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)ms");

    @Test
    void 응답이_지연돼_timeout으로_취소돼도_소요시간이_기록된다() {
        ExternalCallRecorder recorder = new ExternalCallRecorder();
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setAttribute(LogAttributes.EXTERNAL_CALLS, recorder);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));

        try {
            ClientRequest request = ClientRequest.create(
                            HttpMethod.GET, URI.create("https://apis.data.go.kr/B551011/KorService2/detailCommon2"))
                    .build();

            // 실제 어댑터의 지연 응답을 흉내낸다 — 5초짜리 응답에 50ms 짜리 짧은 timeout 을 하류에 건다.
            ExchangeFunction stalled = req -> Mono.delay(Duration.ofSeconds(5))
                    .then(Mono.just(ClientResponse.create(HttpStatus.OK).build()));

            ExchangeFilterFunction filter = ExternalCallLoggingFilter.create();

            assertThrows(
                    RuntimeException.class,
                    () -> filter.filter(request, stalled).timeout(Duration.ofMillis(50)).block());

            assertFalse(recorder.isEmpty(), "취소된 호출도 수집기에 기록돼야 한다");
            long recordedMillis = extractMillis(recorder.summary());
            assertTrue(recordedMillis > 0, "취소된 호출의 소요시간이 0보다 커야 한다: " + recorder.summary());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void 요청_밖이면_수집기가_없어_계측_없이_그대로_통과한다() {
        RequestContextHolder.resetRequestAttributes();

        ClientRequest request = ClientRequest.create(
                        HttpMethod.GET, URI.create("https://apis.data.go.kr/B551011/KorService2/detailCommon2"))
                .build();
        ClientResponse response = ClientResponse.create(HttpStatus.OK).build();
        ExchangeFunction passthrough = req -> Mono.just(response);

        ClientResponse result = ExternalCallLoggingFilter.create().filter(request, passthrough).block();

        assertTrue(result == response);
    }

    private static long extractMillis(String summary) {
        Matcher matcher = DURATION_PATTERN.matcher(summary);
        assertTrue(matcher.find(), "요약에 소요시간(ms)이 없다: " + summary);
        return Long.parseLong(matcher.group(1));
    }
}
