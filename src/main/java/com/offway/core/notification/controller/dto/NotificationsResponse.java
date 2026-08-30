package com.offway.core.notification.controller.dto;

import com.offway.core.notification.service.dto.MyNotifications;
import java.util.List;
import java.util.Map;

/**
 * 알림 목록 응답(#263) — 한 페이지 + 안읽음 전체 수.
 *
 * @param notifications 이 페이지의 알림. 최근 것부터
 * @param unreadCount 안 읽은 알림 <b>전체</b> 개수. 홈 배지가 쓰는 값이라 페이지와 무관하다
 */
public record NotificationsResponse(List<NotificationResponse> notifications, long unreadCount) {

    public static NotificationsResponse from(MyNotifications myNotifications) {
        Map<Long, String> regionNames = myNotifications.regionNameByCourseId();
        return new NotificationsResponse(
                myNotifications.notifications().stream()
                        .map(notification -> NotificationResponse.from(
                                notification, regionNameOf(regionNames, notification.getCourseId())))
                        .toList(),
                myNotifications.unreadCount());
    }

    /**
     * 코스가 없는 알림도, 코스가 지워진 알림도 여기서 {@code null} 이 된다.
     *
     * <p><b>{@code courseId} 가 null 인 경우를 먼저 끊는다.</b> 지도가 비어 있을 때 {@code Map.of()} 는
     * 불변 맵이라 {@code get(null)} 이 NPE 를 낸다 — 코스와 무관한 알림 하나가 목록 전체를 500 으로
     * 만들었다.
     */
    private static String regionNameOf(Map<Long, String> regionNames, Long courseId) {
        return courseId == null ? null : regionNames.get(courseId);
    }
}
