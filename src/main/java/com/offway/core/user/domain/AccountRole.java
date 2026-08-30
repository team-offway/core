package com.offway.core.user.domain;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * access 토큰이 싣는 권한(#342).
 *
 * <p><b>역할을 문자열로 흘리지 않는다.</b> Spring Security 는 {@code "ROLE_"} 접두어를 붙인 이름을 권한으로
 * 쓰고 {@code hasRole} 은 접두어 없는 이름을 받는데, 그 둘을 각자 적으면 한 글자 차이로 조용히 아무에게도
 * 안 걸리는 규칙이 만들어진다. 여기서 한 번만 정한다.
 */
public enum AccountRole {

    /** 앱 사용자. 로그인한 모두가 갖는다 — 상태를 바꾸는 요청이 요구하는 역할이다. */
    USER,

    /**
     * 백오피스. 화이트리스트({@link AdminAccount})에 있는 계정만 받는다.
     *
     * <p>Basic 자격증명에는 <b>절대 주지 않는다.</b> 브라우저가 캐시된 Basic 을 교차 출처 쓰기에도 붙이는데,
     * 그 판단으로 쓰기를 Bearer 로 좁혀 둔 것이 지금 구조다(#122).
     */
    ADMIN;

    /** Spring Security 가 권한 이름에 요구하는 접두어. {@code hasRole} 쪽에는 붙이지 않는다. */
    private static final String AUTHORITY_PREFIX = "ROLE_";

    /** {@code SimpleGrantedAuthority} 에 넣을 이름. */
    public String authority() {
        return AUTHORITY_PREFIX + name();
    }

    /** {@code hasRole(...)} 에 넣을 이름 — 접두어 없이. */
    public String roleName() {
        return name();
    }

    /**
     * 토큰에 실린 이름을 되돌린다. <b>모르는 이름은 건너뛴다</b> — 상수를 지웠을 때 그 이름을 든 옛 토큰이
     * 예외를 내면, 만료 전까지 그 사용자가 아무것도 못 한다. 권한 하나가 없는 편이 낫다.
     */
    public static Set<AccountRole> parse(Iterable<String> names) {
        Set<AccountRole> roles = new LinkedHashSet<>();
        if (names == null) {
            return roles;
        }
        for (String name : names) {
            byName(name).ifPresent(roles::add);
        }
        return roles;
    }

    private static Optional<AccountRole> byName(String name) {
        return Arrays.stream(values()).filter(role -> role.name().equals(name)).findFirst();
    }
}
