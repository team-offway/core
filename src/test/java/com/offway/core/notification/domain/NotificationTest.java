package com.offway.core.notification.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 알림 도메인 단위 테스트(#263) — 소유자 불변식과 읽음 상태.
 *
 * <p><b>소유 키 형식 검증이 사라졌다</b>(#280). 예전에는 빈 문자열·64자 초과를 400 계약 예외로 막았는데,
 * 그 시절 소유 키는 요청 헤더({@code X-Guest-Id})라 아무 문자열이나 도메인까지 닿았기 때문이다. 지금은
 * {@code @LoginUser} 가 토큰에서 꺼낸 {@code UUID} 라 <b>형식이 타입으로 보장</b>되고, 남는 것은
 * "없으면 코드 버그" 라는 불변식뿐이다 — 그래서 계약 예외가 아니라 {@code NullPointerException} 이다.
 */
class NotificationTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 13, 9, 0);

    private static final UUID OWNER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static Notification.NotificationBuilder valid() {
        return Notification.builder()
                .userId(OWNER_ID)
                .type(NotificationType.TRIP_TOMORROW)
                .createdAt(CREATED_AT);
    }

    @Test
    void 소유자가_없으면_불변식_위반이다() {
        // 멀쩡한 클라이언트가 정상 요청으로 여기 닿을 수 없다 — 인증을 통과했다면 UUID 가 반드시 있다.
        // 그래서 400 계약 예외가 아니라 500 신호(NPE)다.
        assertThrows(NullPointerException.class, () -> Notification.requireOwner(null));
    }

    @Test
    void 소유자가_있으면_그대로_통과한다() {
        assertSame(OWNER_ID, Notification.requireOwner(OWNER_ID));
    }

    @Test
    void 생성할_때도_같은_소유자_규칙을_적용한다() {
        // 누가 만들든 스스로 유효함을 보장하는 최후의 보루 — 서비스를 거치지 않아도 같은 결과여야 한다.
        assertThrows(NullPointerException.class, () -> valid().userId(null).build());
    }

    @Test
    void 종류_없이는_만들_수_없다() {
        assertThrows(NullPointerException.class, () -> valid().type(null).build());
    }

    @Test
    void 생성_시각_없이는_만들_수_없다() {
        // 목록 정렬의 1차 키다. 없으면 최신순이 성립하지 않는다.
        assertThrows(NullPointerException.class, () -> valid().createdAt(null).build());
    }

    @Test
    void 만들면_안_읽은_상태다() {
        // 읽음으로 바꾸는 것은 엔티티가 하지 않는다 — 조건부 UPDATE 가 한다. 그 보장은 통합 테스트가 잠근다.
        Notification notification = valid().build();

        assertFalse(notification.isRead());
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
