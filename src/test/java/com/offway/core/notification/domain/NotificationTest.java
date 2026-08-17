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
    void 읽은_시각이_없으면_안_읽은_것이다() {
        // 읽음으로 바꾸는 것은 엔티티가 하지 않는다 — 조건부 UPDATE 가 한다. 그 보장은 통합 테스트가 잠근다.
        assertFalse(valid().build().isRead());
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
