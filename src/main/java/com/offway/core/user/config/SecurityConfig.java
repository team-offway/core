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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 접근 제어 — <b>자격증명 두 가지를 한 체인에서 받는다.</b>
 *
 * <p>소셜 로그인(ADR 0002)이 붙으면서 자격증명이 둘이 됐다. 하나를 다른 하나로 갈아치우지 않고 둘 다 받는다.
 *
 * <table>
 *   <tr><th>수단</th><th>누가 쓰나</th><th>왜 남기나</th></tr>
 *   <tr><td>{@code Authorization: Bearer <access>}</td><td>앱 사용자</td><td>목표 상태. 요청 주체가 누구인지 알 수 있는 유일한 수단</td></tr>
 *   <tr><td>{@code Authorization: Basic ...}</td><td>팀 · Swagger · apidog</td><td>#122 의 임시 게이트. 사람이 브라우저로 API 를 여는 유일한 수단</td></tr>
 * </table>
 *
 * <p><b>Basic 게이트를 이 PR 에서 걷어내지 않는 이유</b>: #122 는 "소셜 로그인이 붙으면 걷어낸다" 는 전제로 들어왔지만,
 * 걷어내는 조건은 로그인이 <i>존재</i>하는 것이 아니라 <b>모든 호출자가 실제 토큰을 들고 오는 것</b>이다. 앱이 배포되기
 * 전까지 Swagger·apidog 는 provider 토큰을 만들 수 없다. 지금 걷어내면 8080 이 다시 열려 TMAP 하루 50건이 봇 한 마리에
 * 고갈된다 — #122 가 막으려던 바로 그 상황이다. 앱 배포 후 별도 PR 에서 {@code httpBasic} 한 줄과
 * {@link BasicAuthProperties} 를 함께 지운다.
 *
 * <p>인증 실패 응답은 {@link ApiResponseAuthenticationEntryPoint}·{@link ApiAccessDeniedHandler} 가 공통 래퍼
 * 규격으로 만든다 — 이 경로는 {@code GlobalExceptionHandler} 가 닿지 못한다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** 인증 없이 열리는 공개 조회 접두어(#143). 여기 아래는 보기 전용이고 소유자 식별자를 반환하지 않는다. */
    private static final String PUBLIC_PATH_PATTERN = "/api/v1/public/**";

    /**
     * 자격증명을 <b>만들어 주는</b> 경로들. 여기를 잠그면 아무도 토큰을 얻을 수 없어 닭이 먼저냐 달걀이 먼저냐가 된다.
     *
     * <p>{@code /api/v1/auth/**} 로 뭉뚱그리지 않는다 — 로그아웃은 누구의 토큰을 폐기할지 알아야 하므로 반드시
     * 인증이 필요하다. 열 것만 적는 allowlist 라, 나중에 {@code /auth} 아래 새 엔드포인트가 생겨도 기본이 잠김이다.
     */
    private static final String[] CREDENTIAL_ISSUING_PATHS = {
        "/api/v1/auth/callback/*", "/api/v1/auth/reissue", "/api/v1/auth/dev-login"
    };

    /** 공유 웹앱은 브라우저에서 직접 부른다 — 읽기만 하므로 GET 만 연다. */
    private static final String CORS_ALLOWED_METHOD = "GET";

    /** 공개 경로의 허용 오리진 — 아래 {@code corsConfigurationSource} 주석에 근거가 있다. */
    private static final String CORS_ALLOWED_ORIGIN = "*";

    private final BasicAuthProperties basicAuthProperties;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiResponseAuthenticationEntryPoint authenticationEntryPoint;
    private final ApiAccessDeniedHandler apiAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth.requestMatchers(PUBLIC_PATH_PATTERN)
                        .permitAll()
                        .requestMatchers(CREDENTIAL_ISSUING_PATHS)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(apiAccessDeniedHandler))
                // Basic 보다 앞에 둔다. Bearer 를 먼저 해석해 컨텍스트를 채우면 Basic 필터는 자기 헤더가 아니라
                // 그냥 통과하므로, 두 수단이 서로를 막지 않는다.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
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
