package com.offway.core.user.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String HASH = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-07-29T00:00:00Z");

    @Test
    void 발급_직후에는_사용_가능하다() {
        RefreshToken token = RefreshToken.issue(USER_ID, HASH, NOW.plus(60, ChronoUnit.DAYS));

        assertTrue(token.isUsableAt(NOW));
        assertFalse(token.isRevoked());
    }

    @Test
    void 만료_시각에_도달하면_사용할_수_없다() {
        // 경계 — 만료 시각 '이후'가 아니라 '도달'부터 무효다.
        RefreshToken token = RefreshToken.issue(USER_ID, HASH, NOW);

        assertTrue(token.isExpired(NOW));
        assertFalse(token.isUsableAt(NOW));
    }

    @Test
    void 폐기하면_사용할_수_없다() {
        RefreshToken token = RefreshToken.issue(USER_ID, HASH, NOW.plus(60, ChronoUnit.DAYS));

        token.revoke(NOW);

        assertTrue(token.isRevoked());
        assertFalse(token.isUsableAt(NOW));
    }

    @Test
    void 이미_폐기된_토큰은_최초_폐기_시각을_유지한다() {
        // 재사용 감지의 근거라 나중 호출이 시각을 덮어쓰면 안 된다.
        RefreshToken token = RefreshToken.issue(USER_ID, HASH, NOW.plus(60, ChronoUnit.DAYS));
        token.revoke(NOW);

        token.revoke(NOW.plus(1, ChronoUnit.HOURS));

        assertEquals(NOW, token.getRevokedAt());
    }

    @Test
    void 해시가_비면_발급할_수_없다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RefreshToken.issue(USER_ID, "  ", NOW.plus(60, ChronoUnit.DAYS)));
    }
}
