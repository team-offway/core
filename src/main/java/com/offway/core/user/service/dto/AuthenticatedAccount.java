package com.offway.core.user.service.dto;

import com.offway.core.user.domain.AccountRole;
import java.util.Set;
import java.util.UUID;

/**
 * access 토큰에서 꺼낸 요청 주체(#342).
 *
 * <p>예전에는 {@code UUID} 하나였다. 백오피스가 붙으면서 "누구인가" 만으로는 부족해졌다 — 같은 토큰
 * 체계에서 <b>무엇을 할 수 있는가</b>까지 함께 나와야 필터가 권한을 채운다.
 *
 * @param userId 토큰 subject
 * @param roles 토큰이 실은 역할. 옛 토큰(역할 클레임 이전)은 {@link AccountRole#USER} 하나다
 */
public record AuthenticatedAccount(UUID userId, Set<AccountRole> roles) {

    public boolean isAdmin() {
        return roles.contains(AccountRole.ADMIN);
    }
}
