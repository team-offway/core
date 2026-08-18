package com.offway.core.common.config;

import com.offway.core.common.external.CallerAttributionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 요청 경로의 외부 호출에 엔드포인트를 주체로 붙인다(#285).
 *
 * <p>경로를 가리지 않고 전부 건다. 외부 호출은 어느 엔드포인트에서든 나갈 수 있고, 안 거는 경로가 생기면
 * 그만큼이 조용히 미상으로 빠진다.
 */
@Configuration
public class CallerAttributionConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new CallerAttributionInterceptor());
    }
}
