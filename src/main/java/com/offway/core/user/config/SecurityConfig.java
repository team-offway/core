package com.offway.core.user.config;

import com.offway.core.user.domain.AccountRole;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
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
 * <p><b>Basic 으로는 읽기만 할 수 있다.</b> 브라우저는 캐시된 Basic 자격증명을 <b>교차 출처 쓰기 요청</b>에도 붙이고,
 * 공개 GET 경로의 CORS 제한은 그 전송을 막지 못한다(CSRF). 이 서비스는 CSRF 토큰을 쓰지 않는 무상태 API 라, 막는 방법은
 * 자격증명의 힘을 줄이는 쪽이다 — Basic 에는 안전한 메서드(GET·HEAD)만 허용하고 상태를 바꾸는 요청은 Bearer 만 받는다.
 *
 * <p>Basic 을 통째로 걷어내지 않는 이유는 <b>그것이 사람이 서버를 들여다보는 유일한 수단</b>이기 때문이다. Swagger 로
 * 명세를 보는 것도, 배포 스모크가 "적재가 실제로 채워졌는지" 를 확인하는 것도 이 경로를 탄다 — 그 스모크는 89곳 중
 * 42곳에서 멈춘 배포를 실제로 잡아냈다. 걷어내면 그 안전망이 함께 사라진다. 앱이 토큰을 들고 오게 된 뒤 별도 PR 에서
 * {@code httpBasic} 한 줄과 {@link BasicAuthProperties} 를 함께 지운다.
 *
 * <p>인증 실패 응답은 {@link ApiResponseAuthenticationEntryPoint}·{@link ApiAccessDeniedHandler} 가 공통 래퍼
 * 규격으로 만든다 — 이 경로는 {@code GlobalExceptionHandler} 가 닿지 못한다.
 *
 * <h2>소유 키가 {@code user_id} 로 옮겨간 뒤의 경계(#280)</h2>
 *
 * <p>목표 경계는 <b>로그인 없이</b>(코스 생성 · 지역 둘러보기 · 공유 링크 만들기 · 공유 링크 열기) 와
 * <b>로그인 필요</b>(내 코스에 담기 · 연차 · 알림 · 푸시 토큰) 다. 이 중 <b>로그인 필요 쪽만 여기서 닫았다</b> —
 * {@link #USER_OWNED_PATHS} 가 읽기까지 Bearer 를 요구한다.
 *
 * <p>로그인 없이 열어야 할 쪽은 아직 열지 않았다. 공유 링크 열기({@code /api/v1/public/**})만 이미 열려 있고,
 * 나머지(지역 둘러보기 · 코스 생성 · 공유 링크 만들기)는 <b>#122 의 Basic 게이트 뒤에 있다</b>. 그것을 여는 것은
 * 이 전환과 별개의 결정이다 — 배포 스모크가 "무인증 GET 은 401" 을 게이트로 삼아 아니면 롤백하고, 코스 생성은
 * 열면 TourAPI·TMAP 일일 한도가 인증 없이 노출된다. 두 가지를 함께 정리하는 별도 PR 이 필요하다.
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

    /**
     * 상태를 바꾸는 요청에 요구하는 역할 — <b>Bearer 로 온 요청만 갖는다</b>.
     *
     * <p>Basic 사용자에게는 주지 않는다. 브라우저가 자동으로 붙이는 자격증명으로는 쓰기를 못 하게 하려는 것이고,
     * 그것이 CSRF 토큰 없이 무상태 API 를 지키는 방법이다.
     */
    private static final String APP_USER_ROLE = AccountRole.USER.roleName();

    /**
     * 백오피스 — <b>읽기까지</b> 이 역할을 요구한다(#342).
     *
     * <p>GET 을 아래 "나머지 읽기" 규칙에 맡기면 Basic 으로 열려, 팀 밖에서 <b>미공개 항목</b>이 보인다.
     * 어드민 목록에는 아직 게시하지 않은 것과 기간이 지난 것이 전부 들어 있다.
     */
    private static final String ADMIN_PATH_PATTERN = "/api/v1/admin/**";

    private static final String ADMIN_ROLE = AccountRole.ADMIN.roleName();

    /**
     * <b>소유자가 있는 데이터</b> — 내 코스 · 연차 · 알림 · 푸시 토큰. 읽기든 쓰기든 Bearer 를 요구한다(#280).
     *
     * <p>읽기까지 역할을 요구하는 이유는 <b>소유 키가 {@code user_id} 로 바뀌었기</b> 때문이다. 이 경로들은
     * 요청 헤더가 아니라 access 토큰이 넣은 principal 로 대상을 정하는데, Basic 으로 들어온 요청은 principal 이
     * null 이다({@code @LoginUser} 는 JWT 가 넣은 것만 푼다). 그대로 통과시키면 소유자 없이 조회가 돌아
     * "빈 목록 200" 이나 NPE 500 이 나간다 — 규약이 막는 조용한 실패다. 여기서 403 으로 끊는 편이 낫다.
     *
     * <p>{@code /api/v1/courses/**} 안에는 로그인 없이 열어야 할 것(코스 생성 · 공유 링크 만들기)이 섞여 있다.
     * 그것들은 이 규칙보다 <b>앞</b>에 예외로 적어야 하고, 아직 그 결정이 서지 않아 지금은 전부 로그인 뒤에 있다.
     */
    private static final String[] USER_OWNED_PATHS = {
        "/api/v1/courses/**",
        "/api/v1/leaves/me/**",
        "/api/v1/notifications/**",
        "/api/v1/devices/**",
        "/api/v1/users/**",
        "/api/v1/auth/logout"
    };

    /** 사람이 서버를 들여다보는 수단. Basic 이 남아 있는 이유이자, 여기까지가 Basic 의 한계다. */
    private static final String[] DOCS_PATHS = {"/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**"};

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
                        // 백오피스는 읽기·쓰기 모두 ADMIN. 아래 GET 규칙보다 **앞**에 둬야 Basic 으로
                        // 미공개 항목이 새지 않는다 — 순서가 곧 규칙이다.
                        .requestMatchers(ADMIN_PATH_PATTERN)
                        .hasRole(ADMIN_ROLE)
                        // 소유자가 있는 데이터는 읽기도 Bearer 만. Basic 은 principal 이 없어 대상을 정할 수 없다.
                        .requestMatchers(USER_OWNED_PATHS)
                        .hasRole(APP_USER_ROLE)
                        // 나머지 읽기는 두 수단 다 받는다 — Swagger 로 명세를 보고, 스모크가 적재를 확인한다.
                        .requestMatchers(HttpMethod.GET, "/**")
                        .authenticated()
                        .requestMatchers(HttpMethod.HEAD, "/**")
                        .authenticated()
                        // 쓰기는 Bearer 만. Basic 사용자는 이 역할이 없어 여기서 403 이 된다.
                        .anyRequest()
                        .hasRole(APP_USER_ROLE))
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
        // 역할을 주지 않는다 — 쓰기는 APP_USER_ROLE 을 요구하므로 이 계정으로는 상태를 바꿀 수 없다.
        return new InMemoryUserDetailsManager(User.withUsername(basicAuthProperties.username())
                .password(basicAuthProperties.password())
                .authorities(List.of())
                .build());
    }
}
