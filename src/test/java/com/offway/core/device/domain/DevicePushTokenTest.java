package com.offway.core.device.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.offway.core.common.exception.ErrorCategory;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 푸시 토큰 도메인 단위 테스트(#264) — 소유 키·토큰의 계약 검증. */
class DevicePushTokenTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 14, 10, 0);

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void 소유_키가_비면_400_계약예외다(String blank) {
        // 빈 헤더는 @RequestHeader 를 통과하므로 멀쩡한 클라이언트가 정상 요청으로 닿는다 — 500 이면 안 된다.
        DeviceException e = assertThrows(DeviceException.class, () -> DevicePushToken.requireOwner(blank));
        assertEquals(ErrorCategory.BAD_REQUEST, e.errorCode().category());
        assertEquals("DEVICE-001", e.errorCode().code());
    }

    @Test
    void 소유_키가_없으면_400_계약예외다() {
        assertThrows(DeviceException.class, () -> DevicePushToken.requireOwner(null));
    }

    @Test
    void 소유_키가_64자를_넘으면_400_계약예외다() {
        assertThrows(
                DeviceException.class,
                () -> DevicePushToken.requireOwner("g".repeat(DevicePushToken.MAX_OWNER_ID_LENGTH + 1)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void 토큰이_비면_400_계약예외다(String blank) {
        DeviceException e = assertThrows(DeviceException.class, () -> DevicePushToken.requireToken(blank));
        assertEquals(ErrorCategory.BAD_REQUEST, e.errorCode().category());
        assertEquals("DEVICE-002", e.errorCode().code());
    }

    @Test
    void 토큰이_512자를_넘으면_400_계약예외다() {
        assertThrows(
                DeviceException.class,
                () -> DevicePushToken.requireToken("t".repeat(DevicePushToken.MAX_TOKEN_LENGTH + 1)));
    }

    @Test
    void 토큰이_512자면_통과한다() {
        String boundary = "t".repeat(DevicePushToken.MAX_TOKEN_LENGTH);

        assertEquals(boundary, DevicePushToken.requireToken(boundary));
    }

    @Test
    void 예외_메시지에_토큰을_담지_않는다() {
        // detail 은 응답에 그대로 나가고 로그에도 남는다 — 토큰은 비밀값에 준한다.
        String secret = "super-secret-fcm-token";

        DeviceException e = assertThrows(
                DeviceException.class,
                () -> DevicePushToken.register("guest-1", secret.repeat(40), DevicePlatform.IOS, NOW));

        assertEquals("푸시 토큰이 올바르지 않습니다.", e.getMessage());
    }

    @Test
    void 등록하면_처음_시각과_갱신_시각이_같다() {
        DevicePushToken token = DevicePushToken.register("guest-1", "fcm-abc", DevicePlatform.ANDROID, NOW);

        assertEquals(NOW, token.getCreatedAt());
        assertEquals(NOW, token.getUpdatedAt());
        assertEquals(DevicePlatform.ANDROID, token.getPlatform());
    }

    @Test
    void 플랫폼_없이는_만들_수_없다() {
        assertThrows(
                NullPointerException.class, () -> DevicePushToken.register("guest-1", "fcm-abc", null, NOW));
    }
}
