package com.offway.core.notification.controller.dto;

import com.offway.core.notification.service.dto.MyNotifications;
import java.util.List;

/**
 * 알림 목록 응답(#263) — 한 페이지 + 안읽음 전체 수.
 *
 * @param notifications 이 페이지의 알림. 최근 것부터
 * @param unreadCount 안 읽은 알림 <b>전체</b> 개수. 홈 배지가 쓰는 값이라 페이지와 무관하다
 */
public record NotificationsResponse(List<NotificationResponse> notifications, long unreadCount) {

    public static NotificationsResponse from(MyNotifications myNotifications) {
        return new NotificationsResponse(
                myNotifications.notifications().stream()
                        .map(NotificationResponse::from)
                        .toList(),
                myNotifications.unreadCount());
    }
}
