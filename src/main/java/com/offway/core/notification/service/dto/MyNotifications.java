package com.offway.core.notification.service.dto;

import com.offway.core.common.response.PageResponse;
import com.offway.core.notification.domain.Notification;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 알림 목록 조회 결과 — 한 페이지 + <b>페이지와 무관한</b> 안읽음 전체 수(#263).
 *
 * <p>{@link PageResponse.Paged} 를 구현해 컨트롤러가 페이지 메타 네 필드를 손으로 나열하지 않게 한다.
 * service dto 가 응답 타입을 몰라도 되는 접점이다.
 */
public record MyNotifications(
        List<Notification> notifications,
        long unreadCount,
        int page,
        int size,
        long totalElements,
        int totalPages)
        implements PageResponse.Paged {

    public static MyNotifications of(Page<Notification> found, long unreadCount) {
        return new MyNotifications(
                found.getContent(),
                unreadCount,
                found.getNumber(),
                found.getSize(),
                found.getTotalElements(),
                found.getTotalPages());
    }
}
