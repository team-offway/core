package com.offway.core.user.config;

import java.util.List;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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

    /** 인증 없이 열리는 유일한 접두어(#143). 여기 아래는 보기 전용이고 소유자 식별자를 반환하지 않는다. */
    private static final String PUBLIC_PATH_PATTERN = "/api/v1/public/**";

    /** 공유 웹앱은 브라우저에서 직접 부른다 — 읽기만 하므로 GET 만 연다. */
    private static final String CORS_ALLOWED_METHOD = "GET";

    /** 공개 경로의 허용 오리진 — 아래 {@code corsConfigurationSource} 주석에 근거가 있다. */
    private static final String CORS_ALLOWED_ORIGIN = "*";

    private final BasicAuthProperties basicAuthProperties;
    private final ApiResponseAuthenticationEntryPoint authenticationEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 게이트의 첫 예외(#143). 공유 링크를 받은 사람에게는 우리 계정이 없다.
                //
                // **경로 접두어 하나로 좁힌다.** 엔드포인트마다 예외를 흩으면 어느 것이 열려 있는지 한눈에
                // 안 보이고, 나중에 추가되는 엔드포인트가 실수로 열린다. 이 접두어 아래에는 보기 전용이면서
                // 소유자 식별자를 반환하지 않는 것만 둔다.
                .authorizeHttpRequests(auth -> auth.requestMatchers(PUBLIC_PATH_PATTERN)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint));
        return http.build();
    }

    /**
     * 공개 경로에만 CORS 를 연다(#143). <b>오리진은 열어두고 자격증명은 막는다.</b>
     *
     * <p>처음에는 배포 도메인만 허용하려 했다. 그런데 이 경로에서 <b>오리진을 좁혀도 지켜지는 것이
     * 없다</b> — 인증 없이 열린 읽기 전용 경로라 누구나 서버에서 그냥 가져갈 수 있다. CORS 가 막는 것은
     * "브라우저에서 다른 사이트의 JS 가 응답을 읽는 것" 뿐이고, 그건 자기 서버로 프록시하면 우회된다.
     * 실효는 없이 공유 웹앱의 배포 도메인이 정해질 때까지 프론트를 막을 뿐이었다.
     *
     * <p><b>대신 {@code allowCredentials} 는 명시적으로 끈다.</b> 브라우저가 이 경로에 쿠키·인증 헤더를
     * 실어 보내지 못하게 해, 나중에 누가 이 접두어 아래에 인증이 필요한 것을 두더라도 브라우저 자격증명이
     * 딸려가지 않는다. Spring 도 {@code *} 와 credentials 조합을 거부하므로 규약이 코드로 강제된다.
     *
     * <p>이 판단의 전제는 "이 접두어 아래는 전부 공개 읽기 전용" 이다. 그 전제가 깨지는 것을 두면
     * 오리진을 좁히는 것이 아니라 <b>그것을 이 접두어에서 빼야 한다.</b>
     *
     * <p>인증 게이트 뒤의 경로에는 걸지 않는다. 그쪽은 앱이 부르고 브라우저가 부르지 않는다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(CORS_ALLOWED_ORIGIN));
        configuration.setAllowedMethods(List.of(CORS_ALLOWED_METHOD));
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(PUBLIC_PATH_PATTERN, configuration);
        return source;
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
