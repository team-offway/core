package com.offway.core.device.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.device.domain.DevicePlatform;
import com.offway.core.device.domain.DevicePushToken;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 저장소 쓰기 메서드가 <b>트랜잭션 없이도</b> 도는가(#270).
 *
 * <p>질의가 전부 {@code @Modifying} 이라 트랜잭션이 없으면 {@code TransactionRequiredException} 으로
 * 죽는다. 서비스({@code @Transactional})를 거치는 경로에서는 호출자의 트랜잭션에 얹혀 살아 있어, 이 구멍은
 * <b>저장소를 직접 부르는 쪽에서만</b> 드러난다.
 *
 * <p>실제로 그렇게 터졌다 — 발송 경로는 외부 호출이라 트랜잭션 밖인데, 그 경로를 처음 테스트하면서
 * {@code register} 가 CI 에서 4건을 깨뜨렸다. 그래서 서비스를 거치지 않고 <b>저장소를 직접</b> 부른다.
 */
@SpringBootTest
class DevicePushTokenRepositoryIntegrationTest {

    @Autowired
    private DevicePushTokenRepository devicePushTokenRepository;

    @Test
    void 등록은_트랜잭션_없이도_돈다() {
        String owner = "guest-270-repo-register";

        devicePushTokenRepository.register(tokenOf(owner, "token-270-repo-register"));

        assertEquals(1, devicePushTokenRepository.findByOwner(owner).size());
    }

    @Test
    void 소유자로_지우는_것도_트랜잭션_없이_돈다() {
        // 회원 탈퇴(#275)가 이 경로를 쓴다. 서비스에 트랜잭션이 있어 지금은 안 터지지만,
        // 저장소가 홀로 서지 못하면 같은 함정이 그대로 남는다.
        String owner = "guest-270-repo-delete-owner";
        devicePushTokenRepository.register(tokenOf(owner, "token-270-repo-delete-owner"));

        assertEquals(1, devicePushTokenRepository.deleteByOwner(owner));

        assertTrue(devicePushTokenRepository.findByOwner(owner).isEmpty());
    }

    @Test
    void 토큰으로_지우는_것도_트랜잭션_없이_돈다() {
        // FCM 이 죽었다고 답한 토큰 정리 — 외부 호출 직후라 트랜잭션 밖이다.
        String owner = "guest-270-repo-delete-token";
        String token = "token-270-repo-delete-token";
        devicePushTokenRepository.register(tokenOf(owner, token));

        assertEquals(1, devicePushTokenRepository.deleteByToken(token));

        assertTrue(devicePushTokenRepository.findByOwner(owner).isEmpty());
    }

    /** 같은 토큰을 다시 등록하면 행이 늘지 않고 갱신된다 — upsert 라 트랜잭션 경계가 특히 중요하다. */
    @Test
    void 같은_토큰을_다시_등록해도_행이_늘지_않는다() {
        String owner = "guest-270-repo-upsert";
        String token = "token-270-repo-upsert";

        devicePushTokenRepository.register(tokenOf(owner, token));
        devicePushTokenRepository.register(tokenOf(owner, token));

        assertEquals(1, devicePushTokenRepository.findByOwner(owner).size());
        devicePushTokenRepository.deleteByOwner(owner);
    }

    private DevicePushToken tokenOf(String owner, String token) {
        return DevicePushToken.register(owner, token, DevicePlatform.ANDROID, LocalDateTime.now());
    }
}
