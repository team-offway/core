package com.offway.core.user.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * 인증된 사용자 식별자(UUID)를 컨트롤러 파라미터로 받는다.
 *
 * <p>{@link AuthenticationPrincipal} 메타 애노테이션이라 별도 ArgumentResolver 가 필요 없다. principal 은
 * {@link JwtAuthenticationFilter} 가 넣은 {@code UUID} 다.
 *
 * <pre>{@code
 * public ApiResponseBody<Void> logout(@LoginUser UUID userId) { ... }
 * }</pre>
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal
public @interface LoginUser {}
