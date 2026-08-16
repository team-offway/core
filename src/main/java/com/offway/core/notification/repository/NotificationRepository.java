package com.offway.core.notification.repository;

import com.offway.core.notification.domain.Notification;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 알림 영속 port(#263). 구현은 {@link NotificationRepositoryImpl}. */
public interface NotificationRepository {

    Notification save(Notification notification);

    /**
     * 같은 소유자·종류·코스의 알림이 <b>없을 때만</b> 만든다.
     *
     * <p>배치가 재실행되거나 재배포로 주기가 다시 시작돼도 같은 알림이 겹치면 안 된다(#269). 판정을
     * 애플리케이션이 하면(있는지 조회 → 없으면 저장) 두 실행이 동시에 "없다" 를 읽고 둘 다 넣는다.
     * 유니크 제약에 얹어 DB 가 판정하게 한다.
     *
     * @return 이 호출로 실제 만들어졌으면 true. 이미 있었으면 false
     */
    boolean saveIfAbsent(Notification notification);

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
