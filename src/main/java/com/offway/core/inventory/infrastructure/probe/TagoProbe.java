package com.offway.core.inventory.infrastructure.probe;

import com.offway.core.common.config.ExternalApiProperties;
import java.net.URI;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/** TAGO 대중교통(국토부) — 도시코드 목록으로 키·연결만 가볍게 확인. */
@Component
class TagoProbe extends AbstractDataGoKrProbe {

    private static final String BASE =
            "https://apis.data.go.kr/1613000/ArvlInfoInqireService/getCtyCodeList";

    TagoProbe(WebClient externalWebClient, ExternalApiProperties props) {
        super(externalWebClient, props);
    }

    @Override
    protected String name() {
        return "TAGO 대중교통(버스)";
    }

    @Override
    protected URI uri(String serviceKey) {
        return UriComponentsBuilder.fromUriString(BASE)
                .queryParam("serviceKey", serviceKey)
                .queryParam("_type", "json")
                .queryParam("numOfRows", "1")
                .queryParam("pageNo", "1")
                .encode()
                .build()
                .toUri();
    }
}
