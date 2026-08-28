package com.offway.core.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.user.config.AuthProperties;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * provider 갱신 토큰 암호화(#301) 단위 테스트.
 *
 * <p>Spring·DB 없이 돈다. 여기서 보는 것은 "왕복이 되는가" 하나가 아니라 <b>실패했을 때 무엇을 하는가</b> 다 —
 * 이 클래스의 값은 대부분 그쪽에 있다.
 */
class ProviderTokenCipherTest {

    /** AES-256 은 32바이트다. 테스트에서만 쓰는 고정 키라 값 자체에 의미는 없다. */
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private static final String OTHER_KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private static ProviderTokenCipher cipherWith(String keyBase64) {
        return cipherWith("v1", keyBase64 == null ? Map.of() : Map.of("v1", keyBase64));
    }

    /** 버전이 여럿인 상황 — 회전 전후를 함께 든 설정이다. */
    private static ProviderTokenCipher cipherWith(String currentVersion, Map<String, String> keys) {
        return new ProviderTokenCipher(new AuthProperties(
                null, Map.of(), null, new AuthProperties.ProviderToken(currentVersion, keys)));
    }

    @Test
    void 암호화한_값을_다시_원문으로_되돌린다() {
        // Apple 에 원문 그대로 되돌려줘야 해제가 되므로, 왕복이 이 기능의 전제다.
        ProviderTokenCipher cipher = cipherWith(KEY);
        String token = "r_abc123.0.mrxu.J-example-apple-refresh-token";

        String sealed = cipher.encrypt(token).orElseThrow();

        assertNotEquals(token, sealed, "저장 값이 원문과 같으면 암호화가 안 된 것이다");
        assertEquals(token, cipher.decrypt(sealed).orElseThrow());
    }

    @Test
    void 같은_값을_두_번_암호화해도_결과가_다르다() {
        // IV 를 매번 새로 뽑기 때문이다. 같으면 "이 두 사용자가 같은 토큰을 쓴다" 가 DB 만 보고 드러난다.
        ProviderTokenCipher cipher = cipherWith(KEY);

        String first = cipher.encrypt("same-token").orElseThrow();
        String second = cipher.encrypt("same-token").orElseThrow();

        assertNotEquals(first, second);
        assertEquals("same-token", cipher.decrypt(first).orElseThrow());
        assertEquals("same-token", cipher.decrypt(second).orElseThrow());
    }

    @Test
    void 키가_없으면_암호화하지_않고_빈_값을_준다() {
        // 평문으로 흘려 넣지 않는다 — 호출자가 이 빈 값을 보고 저장을 건너뛴다.
        ProviderTokenCipher cipher = cipherWith(null);

        assertFalse(cipher.enabled());
        assertTrue(cipher.encrypt("some-token").isEmpty());
    }

    @Test
    void 저장_형태에_키_버전이_붙는다() {
        // 회전한 뒤 옛 값을 어느 키로 풀지 값만 보고 정할 수 있어야 한다.
        assertTrue(cipherWith(KEY).encrypt("t").orElseThrow().startsWith("v1:"));
    }

    @Test
    void 버전_접두어가_없으면_빈_값이다() {
        // 저장 값을 그대로 돌려주면 그 문자열이 토큰 원문 행세를 하며 Apple 로 나간다.
        // 못 푸는 것과 엉뚱한 값을 보내는 것은 다르다 — 앞은 해제를 건너뛰고, 뒤는 원인도 모른 채 실패한다.
        assertTrue(cipherWith(KEY).decrypt("legacy-plaintext").isEmpty());
    }

    /**
     * <b>회전해도 옛 값이 계속 풀린다.</b> 이 테스트가 이 설계의 이유다.
     *
     * <p>키와 버전을 하나만 들면, 버전을 v2 로 올리는 순간 {@code v1:} 값이 "접두어가 안 맞는 값" 이 되어
     * 그대로 Apple 로 나가거나(옛 구현) 전부 못 푸는 값이 된다.
     */
    @Test
    void 회전한_뒤에도_옛_버전_값이_풀린다() {
        String oldSealed = cipherWith("v1", Map.of("v1", KEY)).encrypt("old-token").orElseThrow();

        // v2 로 올리되 v1 키를 남겨 둔다 — 회전 직후의 실제 설정이다.
        ProviderTokenCipher rotated = cipherWith("v2", Map.of("v1", KEY, "v2", OTHER_KEY));
        String newSealed = rotated.encrypt("new-token").orElseThrow();

        assertTrue(newSealed.startsWith("v2:"), "새 값은 새 버전으로 나가야 한다");
        assertEquals("new-token", rotated.decrypt(newSealed).orElseThrow());
        assertEquals("old-token", rotated.decrypt(oldSealed).orElseThrow(), "옛 값이 못 풀리면 해제가 영영 안 된다");
    }

    @Test
    void 모르는_버전은_빈_값이다() {
        // 옛 키를 설정에서 지운 뒤다. 그 값을 평문으로 되돌리면 안 된다.
        String sealed = cipherWith("v1", Map.of("v1", KEY)).encrypt("token").orElseThrow();

        assertTrue(cipherWith("v2", Map.of("v2", OTHER_KEY)).decrypt(sealed).isEmpty());
    }

    @Test
    void 현재_버전의_키가_없으면_꺼진다() {
        // v2 를 가리키는데 v2 키가 없다 — 설정을 반만 옮긴 상태다. 암호화를 하지 않는다.
        ProviderTokenCipher cipher = cipherWith("v2", Map.of("v1", KEY));

        assertFalse(cipher.enabled());
        assertTrue(cipher.encrypt("token").isEmpty());
    }

    @Test
    void 다른_키로는_풀리지_않고_빈_값이다() {
        // 키를 회전했는데 옛 값이 남은 경우다. 예외로 터뜨리면 탈퇴가 통째로 실패한다 —
        // 해제만 건너뛰고 탈퇴는 성공해야 한다.
        String sealed = cipherWith(KEY).encrypt("token").orElseThrow();

        assertTrue(cipherWith(OTHER_KEY).decrypt(sealed).isEmpty());
    }

    @Test
    void 변조된_값은_풀리지_않는다() {
        // GCM 인증 태그가 잡는다. CBC 였다면 조용히 쓰레기가 나와 Apple 에 그대로 보냈을 것이다.
        ProviderTokenCipher cipher = cipherWith(KEY);
        String sealed = cipher.encrypt("token").orElseThrow();
        String tampered = sealed.substring(0, sealed.length() - 2) + (sealed.endsWith("A") ? "BB" : "AA");

        assertTrue(cipher.decrypt(tampered).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void 빈_입력은_암호화하지_않는다(String blank) {
        assertTrue(cipherWith(KEY).encrypt(blank).isEmpty());
    }

    @Test
    void 널_입력도_빈_값이다() {
        assertTrue(cipherWith(KEY).encrypt(null).isEmpty());
        assertTrue(cipherWith(KEY).decrypt(null).isEmpty());
    }

    @Test
    void 키_길이가_틀리면_부팅에서_터진다() {
        // 조용히 무시하면 "설정했다고 믿는데 안 걸린" 상태가 된다. 설정 실수는 부팅에서 드러나야 한다.
        String tooShort = Base64.getEncoder().encodeToString(new byte[16]);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> cipherWith(tooShort));
        assertTrue(e.getMessage().contains("32"), "몇 바이트여야 하는지가 메시지에 있어야 고칠 수 있다");
    }

    @Test
    void 키가_base64가_아니면_부팅에서_터진다() {
        assertThrows(IllegalStateException.class, () -> cipherWith("not-base64!!!"));
    }

    @Test
    void 긴_토큰도_왕복한다() {
        // 컬럼을 1024 로 넓힌 근거다 — 평문 300자면 저장 443자가 된다.
        ProviderTokenCipher cipher = cipherWith(KEY);
        String longToken = "x".repeat(300);

        String sealed = cipher.encrypt(longToken).orElseThrow();

        assertTrue(sealed.length() < 1024, "저장 값이 컬럼(1024)을 넘으면 저장이 깨진다: " + sealed.length());
        assertEquals(longToken, cipher.decrypt(sealed).orElseThrow());
    }

    @Test
    void 키가_없는데_암호문이_있으면_빈_값이다() {
        // 키를 잃은 상태다. 해제는 못 하지만 탈퇴는 성공해야 한다.
        String sealed = cipherWith(KEY).encrypt("token").orElseThrow();

        Optional<String> read = cipherWith(null).decrypt(sealed);

        assertTrue(read.isEmpty());
    }
}
