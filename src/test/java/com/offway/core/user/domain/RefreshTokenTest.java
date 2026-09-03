package com.offway.core.user.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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

        token.revoke(NOW, RevokedReason.LOGOUT);

        assertTrue(token.isRevoked());
        assertFalse(token.isUsableAt(NOW));
    }

    @Test
    void 이미_폐기된_토큰은_최초_폐기_시각을_유지한다() {
        // 재사용 감지의 근거라 나중 호출이 시각을 덮어쓰면 안 된다.
        RefreshToken token = RefreshToken.issue(USER_ID, HASH, NOW.plus(60, ChronoUnit.DAYS));
        token.revoke(NOW, RevokedReason.ROTATED);

        token.revoke(NOW.plus(1, ChronoUnit.HOURS), RevokedReason.LOGOUT);

        assertEquals(NOW, token.getRevokedAt());
        assertEquals(RevokedReason.ROTATED, token.getRevokedReason(), "사유도 최초 값을 유지해야 한다");
    }

    // ── 유예 창 복구의 대상(#389) ──────────────────────────────

    /**
     * 유예 창 안이어도 <b>회전으로 폐기된 것만</b> 되살린다.
     *
     * <p>시각만 보면 로그아웃도 "방금 폐기됨" 이라 이 창을 통과한다 — 그러면 로그아웃한 뒤 10초 동안
     * 그 토큰으로 세션이 되살아나, 사용자가 끊은 것이 안 끊긴다.
     */
    @ParameterizedTest
    @EnumSource(RevokedReason.class)
    void 유예_창_안이라도_회전으로_폐기된_것만_되살린다(RevokedReason reason) {
        RefreshToken token = RefreshToken.issue(USER_ID, HASH, NOW.plus(60, ChronoUnit.DAYS));
        token.revoke(NOW, reason);

        assertEquals(
                reason == RevokedReason.ROTATED,
                token.recoverableWithin(RefreshToken.ROTATION_GRACE, NOW),
                reason + " 의 복구 여부가 규칙과 다르다");
    }

    @Test
    void 유예_창을_벗어난_회전은_되살리지_않는다() {
        RefreshToken token = RefreshToken.issue(USER_ID, HASH, NOW.plus(60, ChronoUnit.DAYS));
        token.revoke(NOW, RevokedReason.ROTATED);

        Instant tooLate = NOW.plus(RefreshToken.ROTATION_GRACE).plusSeconds(1);

        assertFalse(token.recoverableWithin(RefreshToken.ROTATION_GRACE, tooLate));
    }

    @Test
    void 살아_있는_토큰은_복구_대상이_아니다() {
        RefreshToken token = RefreshToken.issue(USER_ID, HASH, NOW.plus(60, ChronoUnit.DAYS));

        assertFalse(token.recoverableWithin(RefreshToken.ROTATION_GRACE, NOW));
    }

    @Test
    void 해시가_비면_발급할_수_없다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RefreshToken.issue(USER_ID, "  ", NOW.plus(60, ChronoUnit.DAYS)));
    }
}
