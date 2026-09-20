package com.offway.core.inventory.infrastructure.probe;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCallRecorder;
import java.net.URI;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/** TAGO 대중교통(국토부) — 도시코드 목록으로 키·연결만 가볍게 확인. */
@Component
class TagoProbe extends AbstractDataGoKrProbe {

    private static final String BASE =
            "https://apis.data.go.kr/1613000/ArvlInfoInqireService/getCtyCodeList";

    TagoProbe(WebClient externalWebClient, ExternalApiProperties props, ExternalApiCallRecorder callRecorder) {
        super(externalWebClient, props, callRecorder);
    }

    /** 도착정보 서비스의 시군코드 조회를 친다 — TAGO 도착정보 한도다. */
    @Override
    public Optional<ExternalApi> quota() {
        return Optional.of(ExternalApi.BUS_ARRIVAL);
    }

    @Override
    protected String name() {
        return "TAGO 대중교통(버스)";
    }

    @Override
    protected String baseUrl() {
        return BASE;
    }

    @Override
    protected URI uri(String serviceKey) {
        return UriComponentsBuilder.fromUriString(BASE)
                .queryParam("serviceKey", serviceKey)
                .queryParam("_type", "json")
                .queryParam("numOfRows", "1")
                .queryParam("pageNo", "1")
                // **다시 인코딩하지 않는다.** 우리 serviceKey 는 data.go.kr 이 발급한 Encoding 키라
                // `%3D` 같은 값이 이미 들어 있다. `.encode()` 를 태우면 `%253D` 가 되어 게이트웨이가
                // "등록되지 않은 서비스키"(403) 로 거절한다 — 런타임 클라이언트가 build(true) 를 쓰는 이유와 같다.
                .build(true)
                .toUri();
    }
}
