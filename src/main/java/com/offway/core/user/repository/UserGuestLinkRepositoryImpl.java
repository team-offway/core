package com.offway.core.user.repository;

import com.offway.core.user.domain.UserGuestLink;
import java.util.List;
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
    public boolean isLinked(String guestId) {
        return userGuestLinkJpaRepository.existsByGuestId(guestId);
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
