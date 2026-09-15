package com.offway.core.liveactivity.infrastructure.apns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * APNs 설정이 갖춰졌는지, 어느 주소로 쏘는지(#575).
 *
 * <p><b>환경을 잘못 고르면 모든 토큰이 죽은 것처럼 보인다.</b> 개발 빌드가 올린 토큰을 운영 APNs 로
 * 쏘면 {@code 400 BadDeviceToken} 이 오고, 그건 우리 쪽에서 "죽은 토큰" 과 구분되지 않는다 — 멀쩡한
 * 등록이 매일 조금씩 지워진다.
 */
class ApnsPropertiesTest {

    private static final String TOKEN = "80a1b2c3";

    @Test
    void 넷이_다_있어야_보낼_수_있다() {
        assertTrue(properties("team", "key", "p8", "topic").configured());
    }

    @ParameterizedTest(name = "team={0} key={1} p8={2} topic={3}")
    @CsvSource({
        "'', key, p8, topic",
        "team, '', p8, topic",
        "team, key, '', topic",
        "team, key, p8, ''",
    })
    void 하나라도_비면_보낼_수_없다(String teamId, String keyId, String privateKey, String topic) {
        assertFalse(properties(teamId, keyId, privateKey, topic).configured());
    }

    @Test
    void 운영은_운영_주소로_쏜다() {
        assertEquals(
                "https://api.push.apple.com/3/device/" + TOKEN,
                properties("team", "key", "p8", "topic").pushUrl(TOKEN));
    }

    @Test
    void 개발은_sandbox_주소로_쏜다() {
        ApnsProperties sandbox = new ApnsProperties("team", "key", "p8", "topic", true);

        assertEquals("https://api.sandbox.push.apple.com/3/device/" + TOKEN, sandbox.pushUrl(TOKEN));
        assertEquals("sandbox", sandbox.environmentName());
    }

    private static ApnsProperties properties(String teamId, String keyId, String privateKey, String topic) {
        return new ApnsProperties(teamId, keyId, privateKey, topic, false);
    }
}
