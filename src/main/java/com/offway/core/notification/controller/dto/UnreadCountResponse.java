package com.offway.core.notification.controller.dto;

/**
 * 읽음 처리 응답(#263) — 처리 후 남은 안읽음 개수.
 *
 * <p>읽은 직후 앱은 홈 배지를 고쳐야 한다. 안 주면 목록을 한 번 더 부르게 되므로 같은 응답에 실어 보낸다.
 *
 * @param unreadCount 안 읽은 알림 전체 개수
 */
public record UnreadCountResponse(long unreadCount) {

    public static UnreadCountResponse of(long unreadCount) {
        return new UnreadCountResponse(unreadCount);
    }
}
