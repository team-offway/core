package com.offway.core.external.probe;

import com.offway.core.external.ExternalApiProperties;
import java.net.URI;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/** 특일 정보(공휴일·대체공휴일) — 한국천문연구원. */
@Component
class HolidayProbe extends AbstractDataGoKrProbe {

    private static final String BASE =
            "https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo";

    HolidayProbe(WebClient externalWebClient, ExternalApiProperties props) {
        super(externalWebClient, props);
    }

    @Override
    protected String name() {
        return "특일정보(공휴일)";
    }

    @Override
    protected URI uri(String serviceKey) {
        return UriComponentsBuilder.fromUriString(BASE)
                .queryParam("serviceKey", serviceKey)
                .queryParam("solYear", "2026")
                .queryParam("solMonth", "07")
                .queryParam("_type", "json")
                .encode()
                .build()
                .toUri();
    }
}
