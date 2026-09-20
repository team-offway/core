package com.offway.core.inventory.infrastructure.probe;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCallRecorder;
import java.net.URI;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/** 코레일 여객열차 운행정보 — 한국철도공사(B551457). 성공 포맷이 resultCode "0"/"정상"으로 다르다. */
@Component
class KorailProbe extends AbstractDataGoKrProbe {

    private static final String BASE =
            "https://apis.data.go.kr/B551457/run/v2/travelerTrainRunInfo2";

    KorailProbe(WebClient externalWebClient, ExternalApiProperties props, ExternalApiCallRecorder callRecorder) {
        super(externalWebClient, props, callRecorder);
    }

    /**
     * <b>셀 자리가 없다.</b> 우리는 코레일 운행정보를 앱에서 안 부른다 — 이 프로브만 친다. 그래서
     * {@link ExternalApi} 에 항목이 없고, 없는 항목을 억지로 만들면 "우리가 쓰는 API" 목록이 사실과
     * 어긋난다.
     *
     * <p>이 호출도 한도를 쓰긴 한다(하루 48회). 다만 그 한도는 우리가 다른 데 쓸 일이 없는 것이라,
     * 집계에 안 잡혀도 <b>다른 호출을 굶기지 않는다</b> — 세어야 하는 이유가 없다.
     */
    @Override
    public Optional<ExternalApi> quota() {
        return Optional.empty();
    }

    @Override
    protected String name() {
        return "코레일 열차운행정보";
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
                .queryParam("dt", "20260710")
                // **다시 인코딩하지 않는다.** 우리 serviceKey 는 data.go.kr 이 발급한 Encoding 키라
                // `%3D` 같은 값이 이미 들어 있다. `.encode()` 를 태우면 `%253D` 가 되어 게이트웨이가
                // "등록되지 않은 서비스키"(403) 로 거절한다 — 런타임 클라이언트가 build(true) 를 쓰는 이유와 같다.
                .build(true)
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
