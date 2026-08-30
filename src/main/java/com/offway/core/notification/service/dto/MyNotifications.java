package com.offway.core.notification.service.dto;

import com.offway.core.common.response.PageResponse;
import com.offway.core.notification.domain.Notification;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

/**
 * 알림 목록 조회 결과 — 한 페이지 + <b>페이지와 무관한</b> 안읽음 전체 수(#263).
 *
 * <p>{@link PageResponse.Paged} 를 구현해 컨트롤러가 페이지 메타 네 필드를 손으로 나열하지 않게 한다.
 * service dto 가 응답 타입을 몰라도 되는 접점이다.
 *
 * @param regionNameByCourseId 코스별 지역명(#356) — <b>지워진 코스는 아예 없다.</b> 알림은 코스가 사라져도
 *     남으므로(raw ID 참조) 못 찾는 것이 정상이고, 응답에서 {@code null} 이 된다
 */
public record MyNotifications(
        List<Notification> notifications,
        Map<Long, String> regionNameByCourseId,
        long unreadCount,
        int page,
        int size,
        long totalElements,
        int totalPages)
        implements PageResponse.Paged {

    public static MyNotifications of(
            Page<Notification> found, Map<Long, String> regionNameByCourseId, long unreadCount) {
        return new MyNotifications(
                found.getContent(),
                regionNameByCourseId,
                unreadCount,
                found.getNumber(),
                found.getSize(),
                found.getTotalElements(),
                found.getTotalPages());
    }
}
