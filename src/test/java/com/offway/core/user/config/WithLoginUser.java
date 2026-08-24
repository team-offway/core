package com.offway.core.user.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.test.context.support.WithSecurityContext;

/**
 * 로그인한 사용자로 요청을 보낸다 — {@code @LoginUser UUID} 가 이 값으로 풀린다(#280).
 *
 * <p><b>{@code @WithMockUser} 로는 안 된다.</b> 그쪽 principal 은 사용자 이름(String)이라
 * {@link LoginUser}(= {@code @AuthenticationPrincipal})가 {@code UUID} 로 풀지 못하고 <b>null</b> 이 된다.
 * 소유 키가 {@code user_id} 로 옮겨간 뒤로 그 null 은 곧 "주인 없는 조회" 라, 컨트롤러가 빈 목록을 200 으로
 * 내리거나 NPE 500 을 내며 <b>테스트가 통과해도 아무것도 검증하지 않는</b> 상태가 된다.
 *
 * <p>그래서 {@link JwtAuthenticationFilter} 가 만드는 것과 <b>같은 모양</b>의 인증을 넣는다 — principal 은
 * {@code UUID}, 권한은 {@code ROLE_USER}. 필터를 통째로 태우지 않는 이유는 그러면 모든 통합 테스트가
 * 소셜 로그인 왕복(stub 설정 · 토큰 발급 · JSON 파싱)을 거쳐야 하고, 그건 이 테스트들이 검증하려는 것이 아니다.
 *
 * <p>기본은 <b>테스트마다 새 UUID</b> 다. 소유자별 격리를 보려면 값을 고정한다.
 *
 * <pre>{@code
 * @Test @WithLoginUser
 * void 내_코스만_보인다() { ... }
 *
 * @Test @WithLoginUser(OWNER)          // 다른 테스트와 같은 주인으로 묶고 싶을 때
 * void 저장한_코스가_목록에_뜬다() { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@WithSecurityContext(factory = WithLoginUserSecurityContextFactory.class)
public @interface WithLoginUser {

    /** 사용자 UUID 문자열. 비우면 매 요청 새로 발급한다. */
    String value() default "";
}
