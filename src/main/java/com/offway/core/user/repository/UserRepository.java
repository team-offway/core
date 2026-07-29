package com.offway.core.user.repository;

import com.offway.core.user.domain.User;
import java.util.Optional;
import java.util.UUID;

/** 사용자 영속 port. 구현은 {@link UserRepositoryImpl}. */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);
}
