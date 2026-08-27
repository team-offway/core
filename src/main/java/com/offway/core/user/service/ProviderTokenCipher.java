package com.offway.core.user.service;

import com.offway.core.user.config.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
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
 * {@code v1:} 로 남아 있어, 복호화가 어느 키로 풀지 값만 보고 정할 수 있다. 접두어가 없으면 그건 이 변경
 * 이전에 평문으로 들어간 값이라는 뜻이라 그대로 읽는다 — 지금 DB 에는 그런 행이 없지만(2026-08-25 초기화)
 * 판정을 값에 남겨 두면 나중에 되짚을 수 있다.
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

    /** 현재 키 버전. 회전하면 이 값을 올리고 옛 버전 복호화 경로를 남긴다. */
    private static final String CURRENT_VERSION = "v1";

    private static final String VERSION_SEPARATOR = ":";

    /** AES-256. 키가 이 길이가 아니면 설정이 잘못된 것이라 부팅 시점에 걸러야 한다. */
    private static final int KEY_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    public ProviderTokenCipher(AuthProperties properties) {
        this.key = readKey(properties.providerToken().keyBase64());
        if (key == null) {
            // 값 자체는 절대 찍지 않는다. "왜 해제가 안 되는가" 를 나중에 물을 때 이 줄이 답이다.
            log.warn("provider 토큰 암호화 키가 없습니다 — Apple 연결 해제용 토큰을 저장하지 않습니다(탈퇴는 정상)");
        }
    }

    /** 키가 설정돼 있는가 — 없으면 이 기능 전체가 꺼진 것이다. */
    public boolean enabled() {
        return key != null;
    }

    /**
     * 저장할 형태로 바꾼다.
     *
     * @return 암호문. <b>키가 없거나 입력이 비면 빈 값</b> — 호출자는 저장을 건너뛴다
     */
    public Optional<String> encrypt(String plaintext) {
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
            return Optional.of(CURRENT_VERSION + VERSION_SEPARATOR + Base64.getEncoder().encodeToString(out));
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
     * <p>버전 접두어가 없으면 이 변경 이전의 평문이라 그대로 돌려준다. 복호화에 실패하면 빈 값이다 —
     * 키를 바꿨는데 옛 값이 남아 있거나 값이 변조된 경우이고, 그때 해제를 건너뛰는 것이 맞다.
     */
    public Optional<String> decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return Optional.empty();
        }
        if (!stored.startsWith(CURRENT_VERSION + VERSION_SEPARATOR)) {
            return Optional.of(stored);
        }
        if (key == null) {
            log.warn("암호화된 provider 토큰이 있는데 키가 없습니다 — 연결 해제를 건너뜁니다");
            return Optional.empty();
        }
        try {
            byte[] raw = Base64.getDecoder().decode(stored.substring(CURRENT_VERSION.length() + 1));
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(raw, IV_BYTES, raw.length - IV_BYTES);
            return Optional.of(new String(plain, StandardCharsets.UTF_8));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.warn("provider 토큰 복호화 실패 — 연결 해제를 건너뜁니다 (사유={})", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * base64 키를 읽는다. 없으면 null(기능 꺼짐), <b>있는데 길이가 틀리면 예외</b>다.
     *
     * <p>길이가 틀린 키를 조용히 무시하면 "설정했다고 믿는데 안 걸린" 상태가 된다 — 설정 실수는 부팅에서
     * 드러나야 한다.
     */
    private static SecretKeySpec readKey(String keyBase64) {
        if (keyBase64 == null || keyBase64.isBlank()) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(keyBase64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("provider 토큰 암호화 키가 base64 가 아닙니다", e);
        }
        if (bytes.length != KEY_BYTES) {
            throw new IllegalStateException(
                    "provider 토큰 암호화 키는 " + KEY_BYTES + "바이트(AES-256)여야 합니다. 지금 " + bytes.length + "바이트");
        }
        return new SecretKeySpec(bytes, ALGORITHM);
    }
}
