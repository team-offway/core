package com.offway.core.notification.controller.dto;

import com.offway.core.notification.domain.Notification;
import com.offway.core.notification.domain.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 알림 한 건(#263).
 *
 * <p><b>문구가 없다.</b> 종류만 내려주고 아이콘·문구는 앱이 맞춘다 — 프론트가 그렇게 하겠다고 했고,
 * 문구를 서버에 굳히면 이미 쌓인 알림이 옛 문구로 남는다.
 *
 * @param id 알림 식별자. 읽음 처리에 쓴다
 * @param type 알림 종류. 앱이 이 값으로 아이콘·문구를 고른다
 * @param courseId 누르면 이동할 코스. 코스와 무관한 알림이면 null
 * @param read 읽었는지
 * @param createdAt 알림이 만들어진 시각(KST)
 */
public record NotificationResponse(
        long id,
        NotificationType type,
        @Schema(example = "12", nullable = true) Long courseId,
        boolean read,
        LocalDateTime createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getCourseId(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
