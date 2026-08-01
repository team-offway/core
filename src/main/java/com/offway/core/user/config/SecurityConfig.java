package com.offway.core.user.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 임시 인증 게이트(#122) — 8080 을 외부에 열 때 <b>아무나 우리 외부 API 키를 태우지 못하게</b> 막는다.
 * TMAP 경유지 최적화는 하루 50건이라 봇 한 마리로 고갈된다.
 *
 * <p><b>HTTP Basic 을 고른 이유</b>: FE 에 로그인 화면이 없고 소셜 로그인은 provider 클라이언트 ID 가 없어
 * 붙일 수 없다(#93 이 draft 인 이유). Basic 은 로그인 페이지·토큰 저장·만료 관리가 전부 필요 없다 — 앱은
 * 헤더 하나, 브라우저(Swagger)는 기본 인증 팝업으로 통과한다. 무엇보다 #93 이 머지되면 <b>통째로 걷어내기
 * 쉽다</b>. 임시 장치는 제거 비용이 낮은 게 중요하다.
 *
 * <p>세션을 만들지 않는다(STATELESS). 매 요청이 자격증명을 들고 오므로 세션이 없어도 되고, 없어야 앱
 * 클라이언트가 쿠키를 관리하지 않는다. CSRF 도 같은 이유로 끈다 — 브라우저 폼 세션이 없으면 공격면이 없다.
 *
 * <p>계정은 하나뿐이라 인메모리다. 팀 내부용 임시 게이트라 가입·비밀번호 재설정 같은 사용자 관리가 필요 없다.
 * 값은 {@link BasicAuthProperties} 가 소유하고, 비어 있으면 부팅을 막는다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final BasicAuthProperties basicAuthProperties;
    private final ApiResponseAuthenticationEntryPoint authenticationEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint));
        return http.build();
    }

    /**
     * 접두어로 인코더를 판별하는 표준 위임 인코더. 설정값이 {@code {noop}dev}(local)든
     * {@code {bcrypt}$2a$...}(운영)든 같은 설정 키로 받는다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager(User.withUsername(basicAuthProperties.username())
                .password(basicAuthProperties.password())
                .build());
    }
}
