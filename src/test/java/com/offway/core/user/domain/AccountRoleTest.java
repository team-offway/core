package com.offway.core.user.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AccountRoleTest {

    /**
     * Spring Security 는 권한 이름에 접두어를 요구하고 {@code hasRole} 은 접두어 없는 이름을 받는다. 이
     * 둘을 각자 적으면 한 글자 차이로 <b>아무에게도 안 걸리는 규칙</b>이 만들어진다.
     */
    @ParameterizedTest
    @EnumSource(AccountRole.class)
    void 권한_이름은_접두어를_붙이고_역할_이름은_안_붙인다(AccountRole role) {
        assertEquals("ROLE_" + role.name(), role.authority());
        assertEquals(role.name(), role.roleName());
    }

    @Test
    void 토큰에_실린_이름을_되돌린다() {
        assertEquals(Set.of(AccountRole.USER, AccountRole.ADMIN), AccountRole.parse(List.of("USER", "ADMIN")));
    }

    /**
     * 상수를 지웠을 때 그 이름을 든 <b>옛 토큰</b>이 예외를 내면, 만료 전까지 그 사용자가 아무것도 못 한다.
     * 권한 하나가 없는 편이 낫다.
     */
    @Test
    void 모르는_이름은_던지지_않고_건너뛴다() {
        assertEquals(Set.of(AccountRole.USER), AccountRole.parse(List.of("USER", "SUPER_ADMIN")));
    }

    @Test
    void 아는_이름이_없거나_null_이면_빈_집합이다() {
        assertTrue(AccountRole.parse(List.of("SUPER_ADMIN")).isEmpty());
        assertTrue(AccountRole.parse(null).isEmpty());
    }

    /** 소문자는 상수명이 아니다 — 토큰에 실린 값과 정확히 같아야 한다. */
    @Test
    void 대소문자가_다르면_모르는_이름이다() {
        assertTrue(AccountRole.parse(List.of("admin")).isEmpty());
    }
}
