package com.offway.core.user.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * OAuth 인증 기반 접근 제어(ADR 0002). 게스트가 없으므로 API 는 기본이 인증 필요다.
 *
 * <p>인증 실패 응답은 {@link ApiAuthenticationEntryPoint}·{@link ApiAccessDeniedHandler} 가 공통 래퍼 규격으로
 * 만든다 — 이 경로는 {@code GlobalExceptionHandler} 가 닿지 못한다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * 지금 인증을 요구하는 경로. 로그아웃은 누구의 토큰을 폐기할지 알아야 하므로 열 수 없다 — permitAll 로 열면
     * {@code @LoginUser} 가 null 로 들어와 서버 오류가 된다.
     */
    private static final String LOGOUT_PATH = "/api/v1/auth/logout";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;
    private final ApiAccessDeniedHandler apiAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 전면 인증 전환은 FE(플러터) 가 provider 클라이언트 ID 를 확보한 뒤 별도 PR 로 한다.
                // 지금 잠그면 실 provider 토큰을 만들 앱이 없어 apidog 실호출 검증(#42)이 막힌다.
                // 전환 시 이 두 줄을 뒤집는다: anyRequest().authenticated() + 공개 경로 목록.
                .authorizeHttpRequests(auth -> auth.requestMatchers(LOGOUT_PATH)
                        .authenticated()
                        .anyRequest()
                        .permitAll())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(apiAuthenticationEntryPoint)
                        .accessDeniedHandler(apiAccessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
