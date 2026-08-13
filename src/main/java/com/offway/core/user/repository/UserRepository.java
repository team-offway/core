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
}
