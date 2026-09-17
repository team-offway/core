package com.offway.core.liveactivity.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * push-to-start 등록 값의 계약 검증(#583).
 *
 * <p>앱이 보낸 값이 그대로 저장되는 자리라 <b>멀쩡한 클라이언트가 정상 요청으로 닿는다</b> — 불변식이
 * 아니라 계약이고, 그래서 400 이다.
 */
class PushToStartTokenTest {

    private static final UUID OWNER = UUID.randomUUID();

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 20, 12, 0);

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void 토큰이_비면_거절한다(String token) {
        assertThrows(LiveActivityException.class, () -> PushToStartToken.register(OWNER, token, NOW));
    }

    @Test
    void 토큰이_상한을_넘으면_거절한다() {
        String tooLong = "a".repeat(PushToStartToken.MAX_TOKEN_LENGTH + 1);

        assertThrows(LiveActivityException.class, () -> PushToStartToken.register(OWNER, tooLong, NOW));
    }

    @Test
    void 상한_길이는_받는다() {
        String atLimit = "a".repeat(PushToStartToken.MAX_TOKEN_LENGTH);

        assertEquals(atLimit, PushToStartToken.register(OWNER, atLimit, NOW).getToken());
    }

    /**
     * <b>예외 메시지에 토큰이 들어가면 안 된다.</b>
     *
     * <p>detail 은 응답에 그대로 나가고 로그에도 남는데, 이 값을 아는 쪽은 그 기기 잠금화면에 카드를
     * 만들 수 있다(로깅 규약).
     */
    @Test
    void 거절_메시지에_토큰을_담지_않는다() {
        String secret = "abcdef0123456789";

        LiveActivityException e = assertThrows(
                LiveActivityException.class,
                () -> PushToStartToken.register(OWNER, secret.repeat(40), NOW));

        assertFalse(e.errorCode().message().contains(secret), "예외 메시지에 토큰이 새어 나왔다");
    }

    /** 처음 등록이든 재등록이든 같은 값을 만든다 — 가르는 일은 유니크 제약을 쥔 DB 가 한다. */
    @Test
    void 등록하면_두_시각이_같다() {
        PushToStartToken token = PushToStartToken.register(OWNER, "token", NOW);

        assertEquals(NOW, token.getCreatedAt());
        assertEquals(NOW, token.getUpdatedAt());
        assertEquals(OWNER, token.getUserId());
    }
}
