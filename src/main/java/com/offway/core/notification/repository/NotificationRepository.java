package com.offway.core.notification.repository;

import com.offway.core.notification.domain.Notification;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 알림 영속 port(#263). 구현은 {@link NotificationRepositoryImpl}. */
public interface NotificationRepository {

    Notification save(Notification notification);

    /** 소유자의 알림 한 페이지 — 최근 것부터. */
    Page<Notification> findByOwner(String guestId, Pageable pageable);

    /**
     * 소유자 범위로만 찾는다 — id 만으로 남의 알림에 닿을 수 없게.
     *
     * <p>없는 id 와 남의 id 가 <b>같은 결과</b>(빈 값)로 떨어져야 호출자가 둘을 구분해 답할 수 없다.
     */
    Optional<Notification> findOwned(String guestId, Long id);

    /**
     * 안 읽은 알림 개수 — <b>페이지와 무관한 전체 수</b>.
     *
     * <p>홈의 배지가 이 값을 쓴다. 페이지 안에서 세면 20개짜리 첫 페이지에서 배지가 20 에 멈춘다.
     */
    long countUnread(String guestId);

    /**
     * 소유자의 안 읽은 알림을 한 번에 읽음 처리한다.
     *
     * @return 실제로 바뀐 건수
     */
    int markAllRead(String guestId, LocalDateTime readAt);
}
