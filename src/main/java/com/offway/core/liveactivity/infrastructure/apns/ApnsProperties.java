package com.offway.core.liveactivity.infrastructure.apns;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * APNs 직접 연동 설정(#575).
 *
 * <h2>Sign in with Apple 의 {@code .p8} 과 <b>다를 수 있다</b> — 그래서 설정을 따로 받는다</h2>
 *
 * <p>서버는 이미 Apple {@code .p8} 을 하나 들고 있다({@code offway.auth.apple.*}, #287). 로그인 연결
 * 해제에 쓰는 키다. <b>그 키가 APNs 에도 쓰일 수 있다</b> — Apple Developer 는 키 하나에 여러 서비스를
 * 붙일 수 있고, 우리 키가 그 경우다.
 *
 * <p><b>실측(2026-09-15)</b> — 운영의 {@code APPLE_PRIVATE_KEY_BASE64} 로 JWT 를 만들어 APNs 에 더미
 * 토큰으로 쐈다.
 *
 * <pre>
 * api.sandbox.push.apple.com -> 400 {"reason":"BadDeviceToken"}
 * api.push.apple.com         -> 400 {"reason":"BadDeviceToken"}
 * </pre>
 *
 * <p>{@code InvalidProviderToken} 이 아니라 {@code BadDeviceToken} 이다 — APNs 가 provider token 을
 * <b>받아들이고</b> 그다음 단계에서 거절한 것이다. {@code TopicDisallowed} 도 아니었으므로 topic 도 맞다.
 *
 * <p><b>그래도 한 칸에 몰지 않는다.</b> 한 키에 두 서비스가 붙어 있는 것은 우연이고 언제든 분리될 수
 * 있어, 합쳐 두면 그때 한쪽을 바꾸는 순간 다른 쪽이 조용히 망가진다. 지금은 같은 값을 양쪽에 넣는다.
 *
 * <p>예전 이 주석은 "APNs 키는 <b>별도로 발급된 것</b>" 이라고 단정했다. 그 서술 때문에 있는 키를 두고
 * 새로 발급하려는 일이 실제로 있었다(#581) — 확인은 위처럼 <b>쏴 보는 것</b>이 가장 빠르다.
 *
 * <p>FCM 을 쓰고 있어도 이 값은 안 채워져 있을 수 있다. Firebase 콘솔에 키를 <b>업로드한 것</b>과
 * 서버가 그 키로 <b>직접 JWT 를 만드는 것</b>은 별개다.
 *
 * <h2>없으면 발송만 비활성이다</h2>
 *
 * <p>이 레포의 불변식은 "designated 브랜치는 local 프로파일에서 시크릿 없이 부팅 가능" 이다(CLAUDE.md).
 * 값이 없으면 {@link ApnsResult#DISABLED} 로 드러내고 부팅은 막지 않는다.
 *
 * @param teamId Apple Developer 팀 식별자. JWT 의 {@code iss}
 * @param keyId APNs {@code .p8} 키의 식별자. JWT 헤더의 {@code kid}
 * @param privateKeyBase64 {@code .p8} 파일 전체를 base64 로. 개행이 환경변수에 섞이지 않게 한다
 * @param topic {@code apns-topic} 헤더 값. <b>앱 번들 ID 에 {@code .push-type.liveactivity} 를 붙인
 *     것</b>이다(예: {@code com.nth.offway.push-type.liveactivity}). 확장 번들 ID 가 <b>아니다</b> —
 *     틀리면 오류가 아니라 <b>조용히 안 온다</b>. 설정으로 빼 둔 것은 앱 번들 ID 가 바뀌어도 서버를
 *     다시 배포하지 않으려는 것이 아니라, 이 값을 코드에 박으면 그 함정이 리뷰에서 안 보이기 때문이다
 * @param sandbox 개발용 APNs 로 보낼지. <b>토큰은 환경마다 다르다</b> — 개발 빌드가 올린 토큰을 운영
 *     APNs 로 보내면 {@code 400 BadDeviceToken} 이 온다
 */
@ConfigurationProperties(prefix = "offway.apns")
public record ApnsProperties(
        String teamId, String keyId, String privateKeyBase64, String topic, boolean sandbox) {

    /** 운영 APNs. */
    private static final String PRODUCTION_HOST = "https://api.push.apple.com";

    /** 개발(sandbox) APNs — Xcode 로 직접 설치한 빌드의 토큰은 이쪽만 받는다. */
    private static final String SANDBOX_HOST = "https://api.sandbox.push.apple.com";

    /** 기기 토큰 하나에 보내는 경로. 뒤에 토큰을 붙인다. */
    private static final String DEVICE_PATH = "/3/device/";

    /** 넷이 다 있어야 APNs 와 이야기할 수 있다. 하나라도 없으면 발송이 비활성이다. */
    public boolean configured() {
        return hasText(teamId) && hasText(keyId) && hasText(privateKeyBase64) && hasText(topic);
    }

    /** 이 토큰으로 보낼 주소. */
    public String pushUrl(String deviceToken) {
        return (sandbox ? SANDBOX_HOST : PRODUCTION_HOST) + DEVICE_PATH + deviceToken;
    }

    /** 로그에 남길 환경 이름 — 어느 쪽으로 쐈는지를 모르면 {@code BadDeviceToken} 의 원인을 못 가른다. */
    public String environmentName() {
        return sandbox ? "sandbox" : "production";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
