package com.offway.core.user.repository;

import com.offway.core.user.domain.User;
import java.util.Optional;
import java.util.UUID;

/** 사용자 영속 port. 구현은 {@link UserRepositoryImpl}. */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    /** 탈퇴 — 사용자 행을 지운다. 되돌릴 수 없다(유예 기간·soft delete 는 두지 않았다). */
    void deleteById(UUID id);

    /**
     * 가입한 사용자 수(#610) — 가입 알림이 "몇 명째인가" 를 말하려고 쓴다.
     *
     * <p>탈퇴하면 행이 사라지므로 <b>지금 살아 있는 계정 수</b>다. 누적 가입 이력이 아니라 현재 인원이고,
     * 알림에 쓰는 값으로는 그쪽이 더 정확하다 — "우리 서비스에 몇 명이 있나" 에 답한다.
     */
    long count();
}
