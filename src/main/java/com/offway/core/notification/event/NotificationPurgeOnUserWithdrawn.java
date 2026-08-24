package com.offway.core.notification.event;

import com.offway.core.notification.repository.NotificationRepository;
import com.offway.core.user.event.UserWithdrawn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 탈퇴하면 그 사람의 알림을 지운다. {@code notification} 이 자기 테이블을 스스로 치운다.
 *
 * <p><b>이 리스너가 없었다</b>(#280 에서 발견). 알림은 #263 으로 코스·연차보다 늦게 생겼는데 탈퇴 정리에는
 * 끼지 못했다. 마이그레이션은 알림을 {@code user_id} 소유로 옮겼고 탈퇴 API 설명도 "코스·연차·후기·알림이
 * 전부 사용자에게 묶여 있다" 고 적었는데, 정작 지우는 쪽이 비어 있었다.
 *
 * <p>남으면 되짚을 방법도 없다. FK 를 두지 않는 규약이라 DB 가 대신 지워주지 않고, 행은 <b>없는 사용자를
 * 가리킨 채</b> 남는다. 게다가 알림 본문에는 여행 일정이 담긴다 — 계정이 사라진 뒤 남을 것이 아니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPurgeOnUserWithdrawn {

    private final NotificationRepository notificationRepository;

    @EventListener
    public void on(UserWithdrawn event) {
        int deleted = notificationRepository.deleteByUserId(event.userId());
        log.info("탈퇴 정리 — 알림 {}건 삭제", deleted);
    }
}
