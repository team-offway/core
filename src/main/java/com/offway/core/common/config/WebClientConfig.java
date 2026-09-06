package com.offway.core.common.config;

import com.offway.core.common.external.ExternalApiHealth;
import com.offway.core.common.external.ExternalHealthFilter;
import com.offway.core.common.logging.ExternalCallLoggingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient externalWebClient(ExternalApiHealth externalApiHealth) {
        return WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                // 외부 어댑터가 전부 이 빈 하나를 공유한다 — 필터 한 장이면 아홉 어댑터를 손대지 않고 계측된다.
                .filter(ExternalCallLoggingFilter.create())
                // 같은 이유로 상태 관찰도 여기 붙인다(#474). 따로 쏘지 않고 이미 하는 호출의 결과를 본다.
                .filter(ExternalHealthFilter.create(externalApiHealth))
                .build();
    }

    /**
     * 알림 전송 전용 클라이언트 — 위 빈과 <b>일부러 갈라 둔다</b>.
     *
     * <p><b>알림 채널을 관측 대상으로 삼으면 안 된다.</b> 같은 빈을 쓰면 디스코드가 느린 날 그것이
     * "외부 장애" 로 집계되고, 그 장애를 알리려 또 디스코드를 부른다 — 되먹임이 된다. 우리가 보려는 것은
     * 여행 데이터를 주는 외부지 우리 알림 통로가 아니다.
     *
     * <p>가르지 않으면 빈 순환도 생긴다: 알림 → 이 클라이언트 → 상태 판정 → 알림. 운영에서만 디스코드
     * 구현이 떠서 로컬·테스트는 초록인 채 배포가 부팅에 실패한다(실제로 CI 가 그렇게 잡았다).
     */
    @Bean
    public WebClient notifierWebClient() {
        return WebClient.builder().build();
    }
}
