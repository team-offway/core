package com.offway.core.user.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.offway.core.user.domain.AccountRole;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 요청 하나에만 붙이는 로그인 — {@link WithLoginUser} 로는 못 쓰는 두 자리를 메운다(#280).
 *
 * <p><b>왜 애노테이션만으로 부족한가.</b>
 *
 * <ul>
 *   <li><b>한 테스트에 주인이 둘일 때</b> — "남의 코스는 안 보인다" 류는 한 메서드 안에서 서로 다른 사용자로
 *       두 번 요청해야 한다. 애노테이션은 메서드당 하나라 이 시나리오가 통째로 불가능하다. 소유자 격리는
 *       이 이슈가 닫으려는 것 자체라 빠뜨릴 수 없다.
 *   <li><b>풀 스레드에서 보낼 때</b> — {@code SecurityContextHolder} 는 ThreadLocal 이라 동시성 테스트가
 *       띄운 스레드는 애노테이션이 심은 컨텍스트를 상속받지 못한다.
 * </ul>
 *
 * <p>만드는 인증의 모양은 {@link JwtAuthenticationFilter}·{@link WithLoginUserSecurityContextFactory} 와
 * 같아야 한다 — principal 은 {@code UUID}, 권한은 {@code ROLE_USER}. 세 곳이 어긋나면 한쪽만 통과하는
 * 테스트가 생긴다.
 *
 * <pre>{@code
 * mockMvc.perform(get("/api/v1/courses").with(loginAs(OTHER)))
 *        .andExpect(jsonPath("$.data.courses").isEmpty());
 * }</pre>
 */
public final class TestLogins {

    /** {@code SecurityConfig.APP_USER_ROLE} 에 대응하는 권한 이름 — 필터와 같은 값이어야 한다. */
    private static final String APP_USER_AUTHORITY = AccountRole.USER.authority();

    /** 백오피스 권한(#342). 화이트리스트에 있는 계정만 이 권한을 얹은 토큰을 받는다. */
    private static final String ADMIN_AUTHORITY = AccountRole.ADMIN.authority();

    private TestLogins() {}

    /** 이 사용자로 로그인한 요청. */
    public static RequestPostProcessor loginAs(UUID userId) {
        return authentication(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority(APP_USER_AUTHORITY))));
    }

    /**
     * 어드민으로 로그인한 요청(#342).
     *
     * <p><b>{@code ROLE_USER} 를 함께 준다.</b> 어드민도 앱을 그대로 쓰는 사람이라 발급되는 토큰에 둘 다
     * 실린다({@code AuthService.ADMIN_ROLES}). 여기서 ADMIN 만 주면 실제로 나가지 않는 모양을 테스트가
     * 통과시키게 된다.
     */
    public static RequestPostProcessor loginAsAdmin(UUID userId) {
        return authentication(new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority(APP_USER_AUTHORITY), new SimpleGrantedAuthority(ADMIN_AUTHORITY))));
    }

    /**
     * 인증은 됐지만 {@code ROLE_USER} 가 없는 요청 — #122 의 Basic 게이트로 들어온 모양이다.
     *
     * <p>이 모양이 중요한 이유는 <b>principal 이 UUID 가 아니라</b>는 것이다. 소유 데이터 경로가 이걸 통과시키면
     * {@code @LoginUser} 가 null 로 풀려 주인 없는 조회가 돌고, 빈 목록 200 이나 NPE 500 이 나간다.
     * {@code SecurityConfig} 가 403 으로 끊는 근거이고, 그 결정을 잠그려면 테스트가 이 모양을 만들 수 있어야 한다.
     */
    public static RequestPostProcessor basicOnly() {
        return authentication(new UsernamePasswordAuthenticationToken("smoke", null, List.of()));
    }
}
