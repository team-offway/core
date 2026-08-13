package com.offway.core.notification.repository;

import com.offway.core.notification.domain.Notification;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/** port 구현(adapter) — Spring Data 에 위임. */
@Repository
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationJpaRepository notificationJpaRepository;

    @Override
    public Notification save(Notification notification) {
        return notificationJpaRepository.save(notification);
    }

    @Override
    public Page<Notification> findByOwner(String guestId, Pageable pageable) {
        return notificationJpaRepository.findByGuestIdOrderByCreatedAtDescIdDesc(guestId, pageable);
    }

    @Override
    public Optional<Notification> findOwned(String guestId, Long id) {
        return notificationJpaRepository.findByIdAndGuestId(id, guestId);
    }

    @Override
    public long countUnread(String guestId) {
        return notificationJpaRepository.countByGuestIdAndReadAtIsNull(guestId);
    }

    @Override
    public int markAllRead(String guestId, LocalDateTime readAt) {
        return notificationJpaRepository.markAllRead(guestId, readAt);
    }
}
