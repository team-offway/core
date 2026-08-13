package com.offway.core.common.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 디스코드 웹훅 좌표.
 *
 * <p><b>URL 자체가 비밀이다.</b> {@code https://discord.com/api/webhooks/{id}/{token}} 의 마지막 조각을
 * 아는 사람은 누구나 그 채널에 글을 쓸 수 있다. 그래서 이 값은 {@code application-secret.properties}
 * (gitignored)에 두고 {@code .example} 에는 자리표시자만 둔다. 로그에도 남기지 않는다
 * ({@code SensitiveParams} 가 토큰 조각을 가린다).
 *
 * <p><b>없어도 부팅은 된다.</b> 시크릿 없이 로컬이 뜨는 것은 이 레포의 불변식이라, 값이 비면 알림만
 * 조용히 건너뛴다.
 *
 * @param webhookUrl 채널 설정 > 연동 > 웹훅에서 만든 URL. 비면 알림 비활성
 */
@ConfigurationProperties(prefix = "offway.notification.discord")
public record DiscordWebhookProperties(String webhookUrl) {

    /** 보낼 곳이 정해져 있는가. */
    public boolean configured() {
        return webhookUrl != null && !webhookUrl.isBlank();
    }
}
