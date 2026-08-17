package com.offway.core.user.repository;

import com.offway.core.user.domain.UserGuestLink;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 사용자-기기 연결 영속 port(#34). 구현은 {@link UserGuestLinkRepositoryImpl}. */
public interface UserGuestLinkRepository {

    UserGuestLink save(UserGuestLink link);

    /**
     * 이 기기가 이미 누군가에게 붙었는가.
     *
     * <p>{@code boolean} 이 아니라 주인을 돌려준다 — "내 것" 과 "남의 것" 은 다르다. 남의 것이면 이 사용자의
     * 데이터가 이어지지 않는다는 뜻이라 흔적을 남겨야 한다.
     */
    Optional<UserGuestLink> findByGuestId(String guestId);

    /** 이 사용자에게 붙은 기기들. 탈퇴가 지울 대상을 여기서 얻는다. */
    List<UserGuestLink> findByUserId(UUID userId);
}
