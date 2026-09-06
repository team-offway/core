package com.offway.core.inventory.infrastructure.probe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.external.ExternalHealthFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * 프로브가 요청을 낼 때 지켜야 하는 두 가지(#479).
 *
 * <p>둘 다 <b>깨져도 화면이 멀쩡한</b> 종류라 테스트로만 잡힌다 — 하나는 상태 판정이 조용히 헛돌고,
 * 다른 하나는 알림에 비밀값이 섞인다.
 */
class ProbeRequestContractTest {

    /** 이 값이 detail 에 남으면 로그와 디스코드 알림으로 그대로 나간다. */
    private static final String SECRET_KEY = "SUPER-SECRET-SERVICE-KEY";

    private static ExternalApiProperties withKey() {
        return new ExternalApiProperties(
                new ExternalApiProperties.DataGoKr(SECRET_KEY),
                new ExternalApiProperties.Tmap(SECRET_KEY));
    }

    /** 프로브가 실제로 낸 요청을 붙잡는 WebClient. 응답은 호출자가 정한다. */
    private static WebClient capturing(List<ClientRequest> captured, Mono<ClientResponse> response) {
        return WebClient.builder()
                .exchangeFunction(request -> {
                    captured.add(request);
                    return response;
                })
                .build();
    }

    private static List<BiFunction<WebClient, ExternalApiProperties, ExternalApiProbe>> factories() {
        return List.of(
                TourApiProbe::new,
                TourDataLabProbe::new,
                HolidayProbe::new,
                TagoProbe::new,
                KorailProbe::new,
                TmapProbe::new);
    }

    /**
     * <b>프로브 요청에는 필터 제외 표시가 붙어야 한다.</b>
     *
     * <p>빠뜨리면 {@code ExternalHealthFilter} 와 {@code ExternalProbeScheduler} 가 <b>같은 호출을 각각
     * 기록한다.</b> 필터는 200 을 성공으로, 스케줄러는 그 안의 {@code resultCode} 실패를 실패로 적어
     * 카운터가 성공과 실패를 오가고, 연속 실패가 쌓이지 않아 장애 확정선에 영영 닿지 못한다.
     *
     * <p>인터페이스가 이 계약을 강제하지 못하므로(새 프로브가 {@code WebClient} 를 직접 부를 수 있다)
     * 여기서 전수로 확인한다.
     */
    @Test
    void 모든_프로브가_필터_제외_표시를_붙인다() {
        for (BiFunction<WebClient, ExternalApiProperties, ExternalApiProbe> factory : factories()) {
            List<ClientRequest> captured = new ArrayList<>();
            WebClient webClient = capturing(captured,
                    Mono.just(ClientResponse.create(HttpStatus.OK).body("{}").build()));
            ExternalApiProbe probe = factory.apply(webClient, withKey());

            probe.probe();

            assertFalse(captured.isEmpty(), probe.system() + " 가 요청을 내지 않았다");
            for (ClientRequest request : captured) {
                assertTrue(request.attribute(ExternalHealthFilter.SKIP_ATTRIBUTE).isPresent(),
                        probe.system() + " 요청에 필터 제외 표시가 없다 — 기록자가 둘이 된다");
            }
        }
    }

    /**
     * <b>실패 사유에 인증키가 남으면 안 된다</b>(CodeRabbit #484 리뷰).
     *
     * <p>{@code WebClientResponseException} 의 메시지는 요청 URL 을 통째로 담고, 우리 외부 호출은
     * {@code serviceKey} 를 쿼리에 싣는다. 이 값은 어드민 표에만 머물지 않고 <b>상태 판정을 거쳐 로그와
     * 디스코드 알림으로</b> 나간다(#479 가 그 경로를 열었다).
     */
    @Test
    void 실패_사유에_인증키가_남지_않는다() {
        for (BiFunction<WebClient, ExternalApiProperties, ExternalApiProbe> factory : factories()) {
            // 실제 WebClient 가 던지는 모양 그대로 — 메시지에 요청 URL 이 통째로 실린다.
            //
            // stub 응답(ClientResponse.create)으로는 이 상황을 못 만든다. 그쪽은 요청을 모르므로
            // 예외 메시지에 URL 이 안 들어가고, 그러면 이 테스트가 아무것도 재지 않는다.
            WebClient webClient = capturing(new ArrayList<>(), Mono.error(urlBearingFailure()));
            ExternalApiProbe probe = factory.apply(webClient, withKey());

            ProbeResult result = probe.probe();

            assertFalse(result.detail().contains(SECRET_KEY),
                    probe.system() + " 의 실패 사유에 인증키가 그대로 남았다: " + result.detail());
        }
    }

    /** 요청 URL 을 메시지에 담은 실패 — {@code serviceKey} 가 쿼리에 실려 있다. */
    private static WebClientResponseException urlBearingFailure() {
        return WebClientResponseException.create(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error from GET "
                        + "https://apis.data.go.kr/B551011/KorService2/areaBasedList2"
                        + "?serviceKey=" + SECRET_KEY + "&numOfRows=1",
                HttpHeaders.EMPTY,
                new byte[0],
                null);
    }
}
