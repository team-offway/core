package com.offway.core.notification.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.common.response.PageResponse;
import com.offway.core.notification.controller.dto.NotificationsResponse;
import com.offway.core.notification.controller.dto.UnreadCountResponse;
import com.offway.core.notification.service.NotificationService;
import com.offway.core.notification.service.dto.MyNotifications;
import com.offway.core.user.config.LoginUser;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림 API(#263).
 *
 * <p><b>소유자를 요청에서 받지 않는다</b>(#280). 예전에는 {@code X-Guest-Id} 헤더가 소유 키였는데, 서버가
 * 그 값을 검증할 방법이 없어 남의 값을 적어 보내면 남의 알림을 읽고 읽음 처리할 수 있었다. 지금은 토큰에서
 * 꺼낸 {@code UUID} 라 자칭이 불가능하다.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController implements NotificationApi {

    private final NotificationService notificationService;

    @Override
    @GetMapping
    public ApiResponseBody<NotificationsResponse> notifications(
            @LoginUser UUID userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        MyNotifications myNotifications = notificationService.myNotifications(userId, page, size);
        return ApiResponseBody.ok(NotificationsResponse.from(myNotifications), PageResponse.of(myNotifications));
    }

    @Override
    @PatchMapping("/{notificationId}/read")
    public ApiResponseBody<UnreadCountResponse> read(@LoginUser UUID userId, @PathVariable long notificationId) {
        return ApiResponseBody.ok(UnreadCountResponse.of(notificationService.markRead(userId, notificationId)));
    }

    @Override
    @PostMapping("/read-all")
    public ApiResponseBody<UnreadCountResponse> readAll(@LoginUser UUID userId) {
        return ApiResponseBody.ok(UnreadCountResponse.of(notificationService.markAllRead(userId)));
    }
}
