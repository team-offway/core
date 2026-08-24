package com.offway.core.user.config;

import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

/**
 * {@link WithLoginUser} 가 넣을 인증을 만든다 — {@link JwtAuthenticationFilter} 와 <b>같은 모양</b>이어야 한다.
 *
 * <p>principal 이 {@code UUID} 가 아니면 {@code @LoginUser} 가 조용히 null 로 풀리고, 권한 이름이 다르면
 * {@code SecurityConfig} 가 403 으로 끊는다. 둘 다 필터 쪽 값을 그대로 따라간다.
 */
public class WithLoginUserSecurityContextFactory implements WithSecurityContextFactory<WithLoginUser> {

    /** {@code SecurityConfig.APP_USER_ROLE} 에 대응하는 권한 이름 — 필터와 같은 값이어야 한다. */
    private static final String APP_USER_AUTHORITY = "ROLE_USER";

    @Override
    public SecurityContext createSecurityContext(WithLoginUser annotation) {
        UUID userId = annotation.value().isBlank() ? UUID.randomUUID() : UUID.fromString(annotation.value());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority(APP_USER_AUTHORITY))));
        return context;
    }
}
