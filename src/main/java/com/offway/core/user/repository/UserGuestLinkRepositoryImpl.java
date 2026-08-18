package com.offway.core.user.repository;

import com.offway.core.user.domain.UserGuestLink;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserGuestLinkRepositoryImpl implements UserGuestLinkRepository {

    private final UserGuestLinkJpaRepository userGuestLinkJpaRepository;

    @Override
    public UserGuestLink save(UserGuestLink link) {
        return userGuestLinkJpaRepository.save(link);
    }

    @Override
    public Optional<UserGuestLink> findByGuestId(String guestId) {
        return userGuestLinkJpaRepository.findByGuestId(guestId);
    }

    @Override
    public List<UserGuestLink> findByUserId(UUID userId) {
        return userGuestLinkJpaRepository.findByUserId(userId);
    }

    @Override
    public int deleteByUserId(UUID userId) {
        return userGuestLinkJpaRepository.deleteByUserId(userId);
    }
}
