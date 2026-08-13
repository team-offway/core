package com.offway.core.notification.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.common.response.PageResponse;
import com.offway.core.notification.controller.dto.NotificationsResponse;
import com.offway.core.notification.controller.dto.UnreadCountResponse;
import com.offway.core.notification.service.NotificationService;
import com.offway.core.notification.service.dto.MyNotifications;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController implements NotificationApi {

    /** 소유 키 — 코스·연차와 같은 헤더를 쓴다. 인증이 붙는 날 함께 옮긴다. */
    private static final String GUEST_HEADER = "X-Guest-Id";

    private final NotificationService notificationService;

    @Override
    @GetMapping
    public ApiResponseBody<NotificationsResponse> notifications(
            @RequestHeader(GUEST_HEADER) String guestId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        MyNotifications myNotifications = notificationService.myNotifications(guestId, page, size);
        return ApiResponseBody.ok(NotificationsResponse.from(myNotifications), PageResponse.of(myNotifications));
    }

    @Override
    @PatchMapping("/{notificationId}/read")
    public ApiResponseBody<UnreadCountResponse> read(
            @RequestHeader(GUEST_HEADER) String guestId, @PathVariable long notificationId) {
        return ApiResponseBody.ok(UnreadCountResponse.of(notificationService.markRead(guestId, notificationId)));
    }

    @Override
    @PostMapping("/read-all")
    public ApiResponseBody<UnreadCountResponse> readAll(@RequestHeader(GUEST_HEADER) String guestId) {
        return ApiResponseBody.ok(UnreadCountResponse.of(notificationService.markAllRead(guestId)));
    }
}
