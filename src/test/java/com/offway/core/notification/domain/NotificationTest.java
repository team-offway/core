package com.offway.core.notification.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.exception.ErrorCategory;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 알림 도메인 단위 테스트(#263) — 소유 키 계약과 읽음 상태 전이. */
class NotificationTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 13, 9, 0);

    private static Notification.NotificationBuilder valid() {
        return Notification.builder()
                .guestId("guest-1")
                .type(NotificationType.TRIP_TOMORROW)
                .createdAt(CREATED_AT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void 소유_키가_비면_400_계약예외다(String blank) {
        // 빈 헤더는 @RequestHeader 를 통과하므로 멀쩡한 클라이언트가 정상 요청으로 닿는다 — 500 이면 안 된다.
        NotificationException e =
                assertThrows(NotificationException.class, () -> Notification.requireOwner(blank));
        assertEquals(ErrorCategory.BAD_REQUEST, e.errorCode().category());
        assertEquals("NOTIFICATION-001", e.errorCode().code());
    }

    @Test
    void 소유_키가_없으면_400_계약예외다() {
        assertThrows(NotificationException.class, () -> Notification.requireOwner(null));
    }

    @Test
    void 소유_키가_64자를_넘으면_400_계약예외다() {
        String tooLong = "g".repeat(Notification.MAX_OWNER_ID_LENGTH + 1);
        assertThrows(NotificationException.class, () -> Notification.requireOwner(tooLong));
    }

    @Test
    void 소유_키가_64자면_통과한다() {
        String boundary = "g".repeat(Notification.MAX_OWNER_ID_LENGTH);
        assertEquals(boundary, Notification.requireOwner(boundary));
    }

    @Test
    void 생성할_때도_같은_소유_키_규칙을_적용한다() {
        // 누가 만들든 스스로 유효함을 보장하는 최후의 보루 — 서비스를 거치지 않아도 같은 결과여야 한다.
        assertThrows(NotificationException.class, () -> valid().guestId(" ").build());
    }

    @Test
    void 종류_없이는_만들_수_없다() {
        assertThrows(NullPointerException.class, () -> valid().type(null).build());
    }

    @Test
    void 만들면_안_읽은_상태다() {
        Notification notification = valid().build();

        assertFalse(notification.isRead());
    }

    @Test
    void 읽으면_읽은_시각이_기록된다() {
        Notification notification = valid().build();
        LocalDateTime readAt = CREATED_AT.plusHours(3);

        assertTrue(notification.markRead(readAt));
        assertTrue(notification.isRead());
        assertEquals(readAt, notification.getReadAt());
    }

    @Test
    void 이미_읽은_알림을_다시_읽어도_처음_시각을_유지한다() {
        // 재요청을 실패로 만들지 않는다 — 사용자가 원한 상태가 이미 이뤄져 있다.
        Notification notification = valid().build();
        LocalDateTime first = CREATED_AT.plusHours(1);
        notification.markRead(first);

        assertFalse(notification.markRead(CREATED_AT.plusHours(5)));
        assertEquals(first, notification.getReadAt());
    }

    @Test
    void 코스가_붙은_알림은_그_코스를_가리킨다() {
        Notification notification = valid().courseId(12L).build();

        assertEquals(Optional.of(12L), notification.course());
    }

    @Test
    void 코스가_없는_알림도_있다() {
        assertEquals(Optional.empty(), valid().build().course());
    }
}
