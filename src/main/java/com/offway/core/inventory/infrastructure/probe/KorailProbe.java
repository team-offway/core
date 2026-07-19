package com.offway.core.inventory.infrastructure.probe;

import com.offway.core.common.config.ExternalApiProperties;
import java.net.URI;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/** 코레일 여객열차 운행정보 — 한국철도공사(B551457). 성공 포맷이 resultCode "0"/"정상"으로 다르다. */
@Component
class KorailProbe extends AbstractDataGoKrProbe {

    private static final String BASE =
            "https://apis.data.go.kr/B551457/run/v2/travelerTrainRunInfo2";

    KorailProbe(WebClient externalWebClient, ExternalApiProperties props) {
        super(externalWebClient, props);
    }

    @Override
    protected String name() {
        return "코레일 열차운행정보";
    }

    @Override
    protected URI uri(String serviceKey) {
        return UriComponentsBuilder.fromUriString(BASE)
                .queryParam("serviceKey", serviceKey)
                .queryParam("_type", "json")
                .queryParam("numOfRows", "1")
                .queryParam("pageNo", "1")
                .queryParam("dt", "20260710")
                .encode()
                .build()
                .toUri();
    }

    @Override
    protected boolean isSuccess(String body) {
        if (body == null) {
            return false;
        }
        // 코레일은 resultCode "0" + resultMsg "정상" 형식
        return body.contains("\"resultCode\":\"0\"")
                || body.contains("\"resultMsg\":\"정상\"")
                || super.isSuccess(body);
    }
}
