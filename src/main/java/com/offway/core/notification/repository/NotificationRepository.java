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
     * 알림 하나를 읽음 처리한다 — <b>아직 안 읽은 것만</b>.
     *
     * <p>읽고 검사하고 쓰면 동시 요청에서 나중 쪽이 처음 읽은 시각을 덮어쓴다. 판정과 기록을 한 문장으로
     * 합쳐 DB 가 갈라주게 한다({@link #markAllRead} 와 같은 방식).
     *
     * @return 이 호출이 실제로 바꾼 행 수. 0 이면 이미 읽은 알림이다
     */
    int markRead(String guestId, Long id, LocalDateTime readAt);

    /**
     * 소유자의 안 읽은 알림을 한 번에 읽음 처리한다.
     *
     * @return 실제로 바뀐 건수
     */
    int markAllRead(String guestId, LocalDateTime readAt);
}
