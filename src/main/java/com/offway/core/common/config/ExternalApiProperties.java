package com.offway.core.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 외부 API 키. 값이 없어도(빈 문자열) 부팅된다(로컬 실행성 규칙) — 실제 호출만 비활성.
 * data.go.kr 인증키 하나로 특일정보·TourAPI·관광빅데이터·TAGO·코레일 공용.
 */
@ConfigurationProperties(prefix = "offway.external")
public record ExternalApiProperties(DataGoKr dataGoKr, Tmap tmap) {

    public ExternalApiProperties {
        if (dataGoKr == null) {
            dataGoKr = new DataGoKr(null);
        }
        if (tmap == null) {
            tmap = new Tmap(null);
        }
    }

    public record DataGoKr(String serviceKey) {
        public boolean hasKey() {
            return serviceKey != null && !serviceKey.isBlank();
        }
    }

    public record Tmap(String appKey) {
        public boolean hasKey() {
            return appKey != null && !appKey.isBlank();
        }
    }
}
