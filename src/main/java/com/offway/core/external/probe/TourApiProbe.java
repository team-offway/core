package com.offway.core.external.probe;

import com.offway.core.external.ExternalApiProperties;
import java.net.URI;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/** 국문 관광정보 서비스(TourAPI, KorService2) — 한국관광공사. */
@Component
class TourApiProbe extends AbstractDataGoKrProbe {

    private static final String BASE =
            "https://apis.data.go.kr/B551011/KorService2/areaBasedList2";

    TourApiProbe(WebClient externalWebClient, ExternalApiProperties props) {
        super(externalWebClient, props);
    }

    @Override
    protected String name() {
        return "국문관광정보(TourAPI)";
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
                .queryParam("areaCode", "1")
                .encode()
                .build()
                .toUri();
    }
}
