package com.offway.core.user.repository;

import com.offway.core.user.domain.UserGuestLink;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
interface UserGuestLinkJpaRepository extends JpaRepository<UserGuestLink, Long> {

    Optional<UserGuestLink> findByGuestId(String guestId);

    List<UserGuestLink> findByUserId(UUID userId);

    int deleteByUserId(UUID userId);
}
