package com.offway.core.notification.repository;

import com.offway.core.notification.domain.Notification;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** port 구현(adapter) — Spring Data 에 위임. */
@Repository
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationJpaRepository notificationJpaRepository;

    @Override
    public Notification save(Notification notification) {
        return notificationJpaRepository.save(notification);
    }

    /**
     * <b>한 건이 자기 트랜잭션을 갖는다.</b> 배치가 수백 건을 도는데 한 건의 실패가 나머지를 물면 안 된다.
     *
     * <p>{@code @Modifying} native 질의라 트랜잭션이 필요하고, 여기서 경계를 끊어야 호출자가 실패를 잡고
     * 다음 건으로 넘어갈 수 있다. 호출자 트랜잭션 안에서 잡으면 그 트랜잭션이 이미 rollback-only 라
     * 커밋 시점에 터진다.
     *
     * <p><b>그래서 {@code REQUIRES_NEW} 다.</b> 기본값({@code REQUIRED})은 트랜잭션을 가진 호출자가 생기면
     * 조용히 그 트랜잭션에 합류한다 — 위 문단이 설명하는 바로 그 실패가 그때 일어난다. 지금 호출자(배치)는
     * 트랜잭션이 없어 결과가 같지만, <b>같다는 사실이 다음 호출자에게는 보장이 아니다.</b> 문서가 약속하는
     * 성질을 애노테이션이 지키게 둔다.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean saveIfAbsent(Notification notification) {
        return notificationJpaRepository.insertIfAbsent(
                        notification.getGuestId(),
                        notification.getType().name(),
                        notification.course().orElse(null),
                        notification.getCreatedAt())
                > 0;
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

    @Override
    public int markRead(String guestId, Long id, LocalDateTime readAt) {
        return notificationJpaRepository.markRead(guestId, id, readAt);
    }
}
