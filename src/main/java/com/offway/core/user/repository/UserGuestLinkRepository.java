package com.offway.core.user.repository;

import com.offway.core.user.domain.UserGuestLink;
import java.util.List;
import java.util.UUID;

/** 사용자-기기 연결 영속 port(#34). 구현은 {@link UserGuestLinkRepositoryImpl}. */
public interface UserGuestLinkRepository {

    UserGuestLink save(UserGuestLink link);

    /** 이미 누군가에게 붙은 기기인가 — 먼저 로그인한 사용자의 것으로 고정하기 위한 확인. */
    boolean isLinked(String guestId);

    /** 이 사용자에게 붙은 기기들. 탈퇴가 지울 대상을 여기서 얻는다. */
    List<UserGuestLink> findByUserId(UUID userId);
}
