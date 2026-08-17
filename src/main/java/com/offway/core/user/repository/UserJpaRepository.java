package com.offway.core.user.repository;

import com.offway.core.user.domain.User;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface UserJpaRepository extends JpaRepository<User, UUID> {}
