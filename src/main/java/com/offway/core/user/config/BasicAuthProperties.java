package com.offway.core.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 임시 Basic 인증 계정(#122). 소셜 로그인(#93)이 붙기 전까지 8080 외부 노출을 막는 게이트다.
 *
 * <p><b>키와 달리 비어 있으면 부팅을 막는다.</b> 외부 API 키는 없어도 "그 호출만 실패" 라 부팅을 열어두지만
 * (로컬 실행성 규칙), 인증 계정이 비면 <b>서버가 통째로 열린 채 뜬다</b> — 조용히 뜨는 쪽이 훨씬 위험하다.
 * local 프로파일은 {@code application-local.properties} 가 기본 계정을 채우므로 시크릿 없이도 부팅된다.
 *
 * <p>비밀번호는 {@code {noop}dev}·{@code {bcrypt}$2a$...} 처럼 <b>인코더 접두어를 포함</b>한다. 접두어로
 * 판별하므로 local 평문과 운영 해시가 같은 설정 키를 쓴다.
 */
@ConfigurationProperties(prefix = "offway.security.basic")
public record BasicAuthProperties(String username, String password) {

    public BasicAuthProperties {
        requireConfigured(username, "offway.security.basic.username");
        requireConfigured(password, "offway.security.basic.password");
    }

    private static void requireConfigured(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    key + " 가 설정되지 않았습니다 — 인증 없이 서버가 열리는 것을 막기 위해 부팅을 중단합니다.");
        }
    }
}
