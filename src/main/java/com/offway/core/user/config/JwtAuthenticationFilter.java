package com.offway.core.user.config;

import com.offway.core.user.domain.UserException;
import com.offway.core.user.service.TokenIssuer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code Authorization: Bearer <access>} 를 검증해 인증 컨텍스트를 채운다.
 *
 * <p>검증에 실패해도 여기서 예외를 던지지 않는다. 서블릿 필터는 {@code DispatcherServlet} 앞이라 던진 예외를
 * {@code GlobalExceptionHandler} 가 잡지 못해 응답이 공통 래퍼 밖으로 새기 때문이다. 컨텍스트를 비우고 통과시키면
 * 인가 단계에서 {@link ApiAuthenticationEntryPoint} 가 규격에 맞는 401 을 만든다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenIssuer tokenIssuer;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String accessToken = resolveToken(request);
        if (accessToken != null) {
            authenticate(accessToken);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String accessToken) {
        try {
            UUID userId = tokenIssuer.parseAccessToken(accessToken);
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
        } catch (UserException exception) {
            SecurityContextHolder.clearContext();
        }
    }

    private static String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!hasBearerScheme(header)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * {@code Authorization} 이 Bearer 인지 — <b>대소문자를 구분하지 않는다</b>.
     *
     * <p>HTTP 인증 scheme 은 규격상 대소문자를 가리지 않는다(RFC 7235). {@code startsWith("Bearer ")} 로 보면
     * {@code bearer <token>} 을 들고 온 클라이언트가 토큰을 안 보낸 것으로 취급돼, 고칠 데가 없는데 401 을 받는다.
     */
    private static boolean hasBearerScheme(String header) {
        return header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length());
    }
}
