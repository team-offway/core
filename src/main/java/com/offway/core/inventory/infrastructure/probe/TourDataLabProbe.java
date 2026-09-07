package com.offway.core.inventory.infrastructure.probe;

import com.offway.core.common.config.ExternalApiProperties;
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
    protected String baseUrl() {
        return BASE;
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
                // **다시 인코딩하지 않는다.** 우리 serviceKey 는 data.go.kr 이 발급한 Encoding 키라
                // `%3D` 같은 값이 이미 들어 있다. `.encode()` 를 태우면 `%253D` 가 되어 게이트웨이가
                // "등록되지 않은 서비스키"(403) 로 거절한다 — 런타임 클라이언트가 build(true) 를 쓰는 이유와 같다.
                .build(true)
                .toUri();
    }
}
