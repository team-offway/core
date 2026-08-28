package com.offway.core.user.service;

import com.offway.core.user.config.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * provider 갱신 토큰을 저장 전에 암호화한다(#301) — 지금은 Apple 연결 해제용 토큰 하나가 대상이다.
 *
 * <h2>왜 해시가 아니라 암호화인가</h2>
 *
 * <p>우리가 발급하는 refresh 토큰은 대조만 하면 되므로 해시해서 넣는다({@code TokenIssuer.hashRefreshToken}).
 * 이건 다르다 — Apple 의 {@code /auth/revoke} 에 <b>원문 그대로 되돌려줘야</b> 해제가 된다. 되돌릴 수 있어야
 * 하므로 단방향 해시를 쓸 수 없다.
 *
 * <h2>무엇을 막으려는 것인가 — KMS 가 아니라 환경변수 키를 고른 이유</h2>
 *
 * <p>이 토큰만으로는 아무것도 못 한다. Apple 의 {@code /auth/token}·{@code /auth/revoke} 는 둘 다
 * {@code client_secret} 을 요구하고 그건 우리 {@code .p8} 로 서명해야 만들어진다. 그래서 <b>DB 와 호스트가
 * 함께 뚫려야</b> 이 값이 쓸모를 갖는다.
 *
 * <p>그렇다면 환경변수 키가 무슨 소용인가 — <b>DB 만 새는 경로를 닫는다.</b> 그건 가정이 아니라 실제로 하는
 * 일이다. 운영 DB 를 고칠 때 {@code mysqldump} 를 받고(2026-08-25 소유 키 전환 때도 그랬다), 그 파일은
 * 디버깅을 위해 호스트 밖으로 옮겨지기도 한다. 덤프 한 장이 그 자체로는 쓸모없게 만드는 것이 이 변경의 값이다.
 *
 * <p><b>KMS 는 택하지 않았다.</b> 로그인·탈퇴 경로에 외부 호출이 하나 늘고(ADR 0001 이 요청 경로의 외부 I/O 를
 * 줄이라고 못박는다), 이 서비스는 EC2 도커 MySQL 하나라 관리형 의존을 더할 근거가 약하다. 키 관리 수준을
 * {@code .p8} 과 같은 자리(환경변수·배포 시크릿)에 두는 것이 지금 구조와 맞는다.
 *
 * <h2>키가 없으면 암호화하지 않고 저장도 하지 않는다</h2>
 *
 * <p>local 프로파일은 시크릿 없이 떠야 한다(로컬 실행성 불변식). 그래서 키가 없어도 부팅을 막지 않는다.
 * 대신 <b>평문으로 흘려 넣지도 않는다</b> — 그건 규약이 막는 조용한 실패다. 키가 없으면 {@link #encrypt} 가
 * 빈 값을 돌려주고, 호출자는 토큰을 저장하지 않는다. 결과는 "해제할 토큰이 없는 사용자" 이고 그건 이미
 * 정상 경로다(탈퇴는 성공하고 Apple 연결 해제만 건너뛴다).
 *
 * <h2>키 버전을 값에 함께 적는다</h2>
 *
 * <p>{@code v1:base64(iv‖ciphertext‖tag)} 형태다. 회전하면 새 값은 {@code v2:} 로 나가고 옛 값은
 * {@code v1:} 로 남아, 복호화가 <b>값만 보고</b> 어느 키로 풀지 정한다. 그래서 설정이 버전별로 키를 든다
 * ({@code AuthProperties.ProviderToken}) — 키를 하나만 들면 회전하는 순간 그 이전 토큰이 전부 못 풀린다.
 *
 * <p><b>모르는 형태는 빈 값으로 끝낸다.</b> 접두어가 없거나 모르는 버전이면 해제를 건너뛴다. 저장 값을
 * 그대로 돌려주지 않는 것이 중요하다 — 그러면 {@code v1:base64...} 라는 문자열이 토큰 원문 행세를 하며
 * Apple 로 나가고, 무엇이 잘못됐는지도 모른 채 실패한다.
 */
@Slf4j
@Component
public class ProviderTokenCipher {

    /** AES-GCM. 인증 태그가 붙어 변조를 복호화 단계에서 잡는다 — CBC 였다면 조용히 쓰레기가 나온다. */
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private static final String ALGORITHM = "AES";

    /** GCM 권장 IV 길이(96비트). 이보다 길면 내부에서 다시 해싱돼 이점이 없다. */
    private static final int IV_BYTES = 12;

    /** 인증 태그 길이(비트). 128 이 GCM 최대이고, 짧게 잡을 이유가 없다. */
    private static final int TAG_BITS = 128;

    private static final String VERSION_SEPARATOR = ":";

    /** AES-256. 키가 이 길이가 아니면 설정이 잘못된 것이라 부팅 시점에 걸러야 한다. */
    private static final int KEY_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    /** 지금 암호화에 쓰는 버전. 저장 값의 접두어로 나간다. */
    private final String currentVersion;

    /** 버전 → 키. 회전해도 옛 버전이 남아 있어 그때 만든 값이 계속 풀린다. */
    private final Map<String, SecretKeySpec> keys;

    public ProviderTokenCipher(AuthProperties properties) {
        AuthProperties.ProviderToken config = properties.providerToken();
        this.currentVersion = config.currentVersion();
        Map<String, SecretKeySpec> parsed = new LinkedHashMap<>();
        config.keys().forEach((version, keyBase64) -> parsed.put(version, readKey(version, keyBase64)));
        this.keys = Map.copyOf(parsed);
        if (!enabled()) {
            // 값 자체는 절대 찍지 않는다. "왜 해제가 안 되는가" 를 나중에 물을 때 이 줄이 답이다.
            log.warn("provider 토큰 암호화 키가 없습니다(버전={}) — Apple 연결 해제용 토큰을 저장하지 않습니다(탈퇴는 정상)",
                    currentVersion);
        }
    }

    /** 현재 버전의 키가 설정돼 있는가 — 없으면 이 기능 전체가 꺼진 것이다. */
    public boolean enabled() {
        return keys.containsKey(currentVersion);
    }

    /**
     * 저장할 형태로 바꾼다.
     *
     * @return 암호문. <b>키가 없거나 입력이 비면 빈 값</b> — 호출자는 저장을 건너뛴다
     */
    public Optional<String> encrypt(String plaintext) {
        SecretKeySpec key = keys.get(currentVersion);
        if (key == null || plaintext == null || plaintext.isBlank()) {
            return Optional.empty();
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[iv.length + sealed.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(sealed, 0, out, iv.length, sealed.length);
            return Optional.of(currentVersion + VERSION_SEPARATOR + Base64.getEncoder().encodeToString(out));
        } catch (GeneralSecurityException e) {
            // 암호화 실패를 삼키고 평문을 넣으면 이 변경이 무의미해진다. 저장을 포기하는 편이 낫다 —
            // 그 결과는 "해제할 토큰이 없음" 이고 이미 정상 경로다.
            log.error("provider 토큰 암호화 실패 — 저장하지 않습니다", e);
            return Optional.empty();
        }
    }

    /**
     * 저장된 값을 Apple 에 되돌려줄 원문으로 바꾼다.
     *
     * <p><b>접두어가 가리키는 버전의 키로 푼다.</b> 회전 뒤에도 옛 값은 옛 키로 계속 풀린다 —
     * 현재 버전으로만 판정하면 {@code v2} 로 올린 순간 {@code v1:} 값이 전부 못 읽는 값이 된다.
     *
     * <p><b>모르는 형태는 빈 값이다 — 평문으로 되돌리지 않는다.</b> 예전에는 접두어가 안 맞으면 저장 값을
     * 그대로 돌려줬는데, 그러면 버전을 올린 순간 {@code v1:base64...} 라는 문자열이 <b>토큰 원문 행세를 하며</b>
     * Apple 로 나간다. 못 푸는 것과 엉뚱한 값을 보내는 것은 다르다 — 앞은 해제를 건너뛰고, 뒤는 무엇이
     * 잘못됐는지도 모른 채 실패한다.
     */
    public Optional<String> decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return Optional.empty();
        }
        int mark = stored.indexOf(VERSION_SEPARATOR);
        if (mark <= 0) {
            log.warn("provider 토큰에 키 버전이 없습니다 — 연결 해제를 건너뜁니다");
            return Optional.empty();
        }
        String version = stored.substring(0, mark);
        SecretKeySpec key = keys.get(version);
        if (key == null) {
            // 회전 중에 옛 키를 설정에서 지웠거나, 아예 키가 없는 환경이다. 어느 쪽이든 풀 수 없다.
            log.warn("provider 토큰 키 버전 {} 의 키가 없습니다 — 연결 해제를 건너뜁니다", version);
            return Optional.empty();
        }
        try {
            byte[] raw = Base64.getDecoder().decode(stored.substring(mark + 1));
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(raw, IV_BYTES, raw.length - IV_BYTES);
            return Optional.of(new String(plain, StandardCharsets.UTF_8));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.warn("provider 토큰 복호화 실패 — 연결 해제를 건너뜁니다 (버전={} 사유={})",
                    version, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * base64 키를 읽는다 — <b>길이가 틀리면 부팅에서 터진다</b>.
     *
     * <p>길이가 틀린 키를 조용히 무시하면 "설정했다고 믿는데 안 걸린" 상태가 된다 — 설정 실수는 부팅에서
     * 드러나야 한다. 어느 버전이 잘못됐는지 함께 적는다: 키가 여럿이면 그 정보 없이는 못 찾는다.
     */
    private static SecretKeySpec readKey(String version, String keyBase64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(keyBase64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("provider 토큰 암호화 키(" + version + ")가 base64 가 아닙니다", e);
        }
        if (bytes.length != KEY_BYTES) {
            throw new IllegalStateException("provider 토큰 암호화 키(" + version + ")는 " + KEY_BYTES
                    + "바이트(AES-256)여야 합니다. 지금 " + bytes.length + "바이트");
        }
        return new SecretKeySpec(bytes, ALGORITHM);
    }
}
