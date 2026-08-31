package com.offway.core.user.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * 웹 로그인 왕복을 잇는 1회용 값(#343) — <b>로그인 CSRF 를 막는다</b>.
 *
 * <h2>무엇을 막나</h2>
 *
 * <p>이것이 없으면 공격자가 <b>자기 계정의 인가 코드</b>로 만든 콜백 주소를 어드민에게 열게 해서, 어드민을
 * 공격자 계정으로 로그인시킬 수 있다. 그 상태로 어드민이 무언가를 등록하면 그 흔적이 공격자 계정에 남는다.
 *
 * <p>막는 방법은 <b>"이 콜백이 내가 시작한 로그인인가"</b> 를 확인하는 것이다. 시작할 때 임의값을 만들어
 * 브라우저 쿠키와 카카오 양쪽에 두고, 돌아왔을 때 둘이 같은지 본다. 공격자는 피해자의 쿠키를 모르므로
 * 같은 값을 실어 보낼 수 없다.
 *
 * <h2>왜 세션이 아니라 쿠키인가</h2>
 *
 * <p>서버가 {@code STATELESS} 라 세션이 없다. 상태를 서버에 두지 않고 브라우저에 맡기되, 서버가 돌려받아
 * 대조하는 값이라 위조로 얻을 것이 없다.
 */
public record OAuthState(String value) {

    /**
     * 임의값의 길이 — 256비트.
     *
     * <p>추측할 수 없으면 되는 값이라 이보다 짧아도 실무상 안전하지만, 이 값이 뚫리면 위 CSRF 가 그대로
     * 열리므로 넉넉히 잡는다. 쿠키·쿼리스트링에 실려도 부담되지 않는 크기다.
     */
    private static final int RANDOM_BYTES = 32;

    /** URL 에 그대로 실리는 값이라 패딩 없는 URL-safe base64 로 만든다. */
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private static final SecureRandom RANDOM = new SecureRandom();

    public OAuthState {
        Objects.requireNonNull(value, "state 는 필수입니다");
        if (value.isBlank()) {
            throw new IllegalArgumentException("state 는 비어 있을 수 없습니다");
        }
    }

    /** 새 왕복을 시작한다. <b>계산으로 만들어지는 값이라 빌더가 아니라 팩토리다.</b> */
    public static OAuthState issue() {
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        return new OAuthState(ENCODER.encodeToString(bytes));
    }

    /**
     * 돌아온 값이 내가 시작할 때 만든 것과 같은가.
     *
     * <p><b>{@code equals} 가 아니라 {@link MessageDigest#isEqual} 로 비교한다.</b> 문자열 비교는 첫 다른
     * 글자에서 멈춰, 걸린 시간이 "앞에서 몇 글자가 맞았는지" 를 알려준다. 한 글자씩 맞춰 가며 값을
     * 알아내는 공격이 성립하는 자리라 길이와 무관하게 같은 시간이 걸리는 비교를 쓴다.
     *
     * <p>{@code null}·빈 값은 그냥 불일치다 — 쿠키가 만료됐거나 콜백에 state 가 없는 경우인데, 둘 다
     * 정상적인 왕복이 아니다.
     */
    public boolean matches(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                value.getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8));
    }
}
