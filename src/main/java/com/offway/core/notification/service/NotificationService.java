package com.offway.core.notification.service;

import com.offway.core.common.response.Paging;
import com.offway.core.notification.domain.Notification;
import com.offway.core.notification.domain.NotificationException;
import com.offway.core.notification.repository.NotificationRepository;
import com.offway.core.notification.service.dto.MyNotifications;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 조회·읽음(#263).
 *
 * <p><b>여기서 알림을 만들지 않는다.</b> 무엇이 알림을 만드는지는 별도 작업이다(여행 전날 배치). 이 서비스는
 * 이미 쌓인 것을 보여주고 읽음을 기록한다.
 *
 * <p>외부 호출이 없어 트랜잭션이 짧다 — 전부 DB 만 만진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** 읽은 시각 기준 시간대. 서비스가 한국 여행을 다루므로 사용자 로캘과 무관하게 KST 다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final NotificationRepository notificationRepository;

    /**
     * 소유자의 알림 한 페이지 + 안읽음 전체 수.
     *
     * <p><b>페이지로 끊는다</b>(api-convention §목록 페이지네이션). 알림은 지우지 않고 쌓이는 데이터라,
     * 상한이 없으면 오래 쓴 사용자의 목록 한 번이 계속 커진다. 기본값·상한은 {@link Paging} 이 소유한다.
     *
     * <p><b>안읽음 수는 따로 센다.</b> 배지가 쓰는 값이라 페이지 안에서 세면 첫 페이지 크기에서 멈춘다.
     */
    @Transactional(readOnly = true)
    public MyNotifications myNotifications(String guestId, Integer page, Integer size) {
        String owner = Notification.requireOwner(guestId);
        Page<Notification> found = notificationRepository.findByOwner(owner, Paging.of(page, size));
        return MyNotifications.of(found, notificationRepository.countUnread(owner));
    }

    /**
     * 알림 하나를 읽음으로 바꾸고 <b>남은 안읽음 수를 돌려준다</b>.
     *
     * <p>개수를 함께 주는 이유: 앱은 읽은 직후 배지를 고쳐야 하는데, 안 주면 목록을 다시 부른다. 클라이언트가
     * 떠안을 일을 서버가 미리 끝낸다.
     *
     * <p>없는 알림과 남의 알림은 <b>똑같이 404</b> 다. 403 으로 나누면 id 를 훑어 남의 알림 존재를 확인할 수
     * 있다({@code CourseStorageService.get} 과 같은 규칙).
     *
     * @return 처리 후 남은 안읽음 개수
     */
    @Transactional
    public long markRead(String guestId, long notificationId) {
        String owner = Notification.requireOwner(guestId);
        Notification notification = notificationRepository
                .findOwned(owner, notificationId)
                .orElseThrow(NotificationException::notificationNotFound);
        if (notification.markRead(LocalDateTime.now(SERVICE_ZONE))) {
            notificationRepository.save(notification);
        }
        return notificationRepository.countUnread(owner);
    }

    /**
     * 소유자의 안 읽은 알림을 전부 읽음 처리하고 <b>남은 안읽음 수를 돌려준다</b>.
     *
     * <p>개수를 다시 센다. 벌크 갱신 직후라 보통 0 이지만, 같은 순간에 새 알림이 들어왔다면 그것까지 읽었다고
     * 답하게 된다 — 화면의 배지가 조용히 거짓말을 한다.
     *
     * @return 처리 후 남은 안읽음 개수
     */
    @Transactional
    public long markAllRead(String guestId) {
        String owner = Notification.requireOwner(guestId);
        int changed = notificationRepository.markAllRead(owner, LocalDateTime.now(SERVICE_ZONE));
        log.info("알림 전체 읽음 처리 changed={}", changed);
        return notificationRepository.countUnread(owner);
    }
}
