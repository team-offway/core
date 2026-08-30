package com.offway.core.notification.controller.dto;

import com.offway.core.notification.domain.Notification;
import com.offway.core.notification.domain.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 알림 한 건(#263).
 *
 * <p><b>문구가 없다.</b> 종류만 내려주고 아이콘·문구는 앱이 맞춘다 — 프론트가 그렇게 하겠다고 했고,
 * 문구를 서버에 굳히면 이미 쌓인 알림이 옛 문구로 남는다.
 *
 * <p><b>{@code regionName} 은 그 원칙을 건드리지 않는다(#356).</b> 지역명은 문구가 아니라 <b>데이터</b>다 —
 * 앱은 여전히 {@code type} 으로 문장을 고르고 이 값을 끼워 넣기만 한다. 문구를 바꿔도 서버 배포 없이 앱만
 * 고치면 되고, 이미 쌓인 알림도 새 문구로 보인다.
 *
 * @param id 알림 식별자. 읽음 처리에 쓴다
 * @param type 알림 종류. 앱이 이 값으로 아이콘·문구를 고른다
 * @param courseId 누르면 이동할 코스. 코스와 무관한 알림이면 null
 * @param regionName 그 코스의 지역(#356). {@code 정선군} 이 아니라 문장에 넣을 <b>{@code 정선}</b> 이다.
 *     <b>코스가 지워졌으면 null</b> — 알림은 코스가 사라져도 남는다. 그때 앱은 지역명 없는 문구로 돌아간다
 * @param read 읽었는지
 * @param createdAt 알림이 만들어진 시각(KST)
 */
@Builder
public record NotificationResponse(
        long id,
        NotificationType type,
        @Schema(example = "12", nullable = true) Long courseId,
        @Schema(example = "정선", nullable = true) String regionName,
        boolean read,
        LocalDateTime createdAt) {

    /**
     * 빌더로 조립하는 이유 — 필드가 여섯이고 <b>앞으로 더 는다</b>(#357 이 알림 id 를 붙일 자리를 보고
     * 있다). 위치 인수는 같은 타입이 이웃할 때 조용히 어긋나는데, 그 조합은 필드가 늘면서 생긴다 —
     * 지금 안 겹친다고 두면 겹치는 날 컴파일이 아무 말도 안 한다.
     */
    public static NotificationResponse from(Notification notification, String regionName) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .courseId(notification.getCourseId())
                .regionName(regionName)
                .read(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
