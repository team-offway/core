package com.offway.core.user.config;

import org.springframework.http.HttpHeaders;

/**
 * 요청이 <b>무엇을 들고 왔는지</b> — {@code Authorization} 헤더의 인증 수단(#41).
 *
 * <p><b>왜 타입인가.</b> 401 하나를 놓고 두 곳이 같은 판정을 한다. 응답 쪽은 "Bearer 를 들고 왔으면
 * 재발급하라는 신호를 준다" 를 정하고, 로그 쪽은 "무엇으로 들어와 실패했는가" 를 남긴다. 문자열 비교를
 * 양쪽에 두면 한쪽만 고쳐져 <b>응답과 로그가 서로 다른 말을 하게</b> 된다.
 *
 * <p><b>이 한 칸이 401 의 성격을 가른다.</b> {@link #BEARER} 면 우리 앱이 만료·위조된 토큰을 들고 온 것이라
 * 앱을 봐야 하고, {@link #NONE} 이면 자격증명 없이 두드린 것이라 대개 스캐너다. 구분이 없으면 401 건수가
 * 늘어도 어느 쪽이 늘었는지 알 수 없다.
 *
 * <p><b>자격증명 자체는 담지 않는다.</b> 이 타입이 아는 것은 수단뿐이고 값은 읽지 않는다 — 오타로 비밀번호가
 * 사용자명 자리에 들어오는 일이 흔하고, 그게 그대로 로그에 박힌다(로깅 규약).
 */
enum AuthScheme {

    /** 앱이 access 토큰을 들고 왔다 — 만료·위조 둘 중 하나다. */
    BEARER("bearer", "Bearer "),

    /** 사람이 브라우저·Swagger 로 들어왔다(#122 의 임시 게이트). */
    BASIC("basic", "Basic "),

    /** 아는 수단이 아니다. 값은 읽지 않으므로 무엇인지는 묻지 않는다. */
    OTHER("other", null),

    /** 자격증명 없이 두드렸다 — 대개 스캐너다. */
    NONE("none", null);

    private final String label;
    private final String prefix;

    AuthScheme(String label, String prefix) {
        this.label = label;
        this.prefix = prefix;
    }

    /**
     * 헤더가 무엇인지 가린다.
     *
     * <p><b>대소문자를 구분하지 않는다.</b> HTTP 인증 scheme 은 규격상 대소문자를 가리지 않는다(RFC 7235).
     * {@code startsWith("Bearer ")} 로 보면 {@code bearer <token>} 을 들고 온 클라이언트가 토큰을 안 보낸
     * 것으로 취급돼, 고칠 데가 없는데 401 을 받는다.
     */
    static AuthScheme of(String header) {
        if (header == null || header.isBlank()) {
            return NONE;
        }
        if (BEARER.matches(header)) {
            return BEARER;
        }
        return BASIC.matches(header) ? BASIC : OTHER;
    }

    private boolean matches(String header) {
        return prefix != null && header.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    /** 그 요청의 {@code Authorization} 헤더로 판정한다. */
    static AuthScheme of(jakarta.servlet.http.HttpServletRequest request) {
        return of(request.getHeader(HttpHeaders.AUTHORIZATION));
    }

    /** 로그에 남길 이름. */
    String label() {
        return label;
    }
}
