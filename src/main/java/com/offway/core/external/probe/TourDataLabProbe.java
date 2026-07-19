package com.offway.core.external.probe;

import com.offway.core.external.ExternalApiProperties;
import java.net.URI;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/** 관광빅데이터(광역별 방문자수) — 한국관광공사 DataLabService. */
@Component
class TourDataLabProbe extends AbstractDataGoKrProbe {

    private static final String BASE =
            "https://apis.data.go.kr/B551011/DataLabService/metcoRegnVisitrDDList";

    TourDataLabProbe(WebClient externalWebClient, ExternalApiProperties props) {
        super(externalWebClient, props);
    }

    @Override
    protected String name() {
        return "관광빅데이터(방문자·집중률)";
    }

    @Override
    protected URI uri(String serviceKey) {
        return UriComponentsBuilder.fromUriString(BASE)
                .queryParam("serviceKey", serviceKey)
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "offway")
                .queryParam("_type", "json")
                .queryParam("numOfRows", "1")
                .queryParam("pageNo", "1")
                .queryParam("startYmd", "20260601")
                .queryParam("endYmd", "20260607")
                .encode()
                .build()
                .toUri();
    }
}
