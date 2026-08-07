package com.offway.core.common.config;

import com.offway.core.common.logging.ExternalCallLoggingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient externalWebClient() {
        return WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                // 외부 어댑터가 전부 이 빈 하나를 공유한다 — 필터 한 장이면 아홉 어댑터를 손대지 않고 계측된다.
                .filter(ExternalCallLoggingFilter.create())
                .build();
    }
}
