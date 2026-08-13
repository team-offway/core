package com.offway.core.common.notification;

import com.offway.core.common.logging.RootCause;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 디스코드 웹훅으로 보낸다.
 *
 * <p><b>{@code prod} 에서만 뜬다.</b> 로컬에서 돌면 개발 중에 팀 채널이 울린다. 로컬은
 * {@link LoggingNotifier} 가 같은 문구를 로그로 남겨, 웹훅 없이도 "무엇이 언제 나가는지" 를 확인할 수 있다.
 *
 * <p><b>보내고 기다리지 않는다.</b> {@code send} 는 코스 생성 도중({@code ExternalApiCallRecorder})에
 * 불릴 수 있다. 여기서 응답을 기다리면 디스코드가 느린 날 사용자 요청이 함께 느려진다. 구독만 걸고
 * 곧바로 돌아온다.
 *
 * <p><b>실패는 삼키되 흔적을 남긴다.</b> 알림을 못 보낸 것 때문에 코스 생성이 깨지면 안 되지만, 조용히
 * 죽으면 알림이 없는 것과 같다. 예외 원인을 warn 으로 남긴다 — URL 은 {@link RootCause} 가 마스킹한다.
 */
@Slf4j
@Component
@Profile("prod")
public class DiscordWebhookNotifier implements Notifier {

    /**
     * 전송 상한. 알림은 요청 경로에서 던져지므로 오래 물고 있을 이유가 없다.
     *
     * <p>기다리는 주체가 사용자가 아니라 백그라운드 구독이라 짧게 잡아도 잃는 것이 없다 — 못 보내면
     * 다음 단계에서 다시 기회가 온다.
     */
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(3);

    /** 디스코드가 받는 필드명. 내용 없이 보내면 400 이 온다. */
    private static final String CONTENT_FIELD = "content";

    /**
     * 한 메시지 길이 상한. 디스코드가 2,000자를 넘기면 통째로 거절한다.
     *
     * <p>잘라서라도 보낸다 — 길다는 이유로 아무것도 못 받는 것보다 앞부분이라도 보는 편이 낫다.
     */
    private static final int MAX_CONTENT_LENGTH = 2_000;

    private static final String TRUNCATED = "…";

    private final WebClient webClient;
    private final DiscordWebhookProperties properties;

    public DiscordWebhookNotifier(WebClient externalWebClient, DiscordWebhookProperties properties) {
        this.webClient = externalWebClient;
        this.properties = properties;
        if (!properties.configured()) {
            log.warn("디스코드 웹훅 URL 이 없어 알림을 보내지 않습니다 — 설정하려면 offway.notification.discord.webhook-url");
        }
    }

    @Override
    public void send(String message) {
        if (!properties.configured() || message == null || message.isBlank()) {
            return;
        }
        webClient.post()
                .uri(properties.webhookUrl())
                .bodyValue(Map.of(CONTENT_FIELD, trimToLimit(message)))
                .retrieve()
                .toBodilessEntity()
                .timeout(SEND_TIMEOUT)
                .subscribe(
                        sent -> log.debug("디스코드 알림 전송"),
                        error -> log.warn("디스코드 알림 실패 cause={}", RootCause.of(error)));
    }

    private static String trimToLimit(String message) {
        return message.length() <= MAX_CONTENT_LENGTH
                ? message
                : message.substring(0, MAX_CONTENT_LENGTH - TRUNCATED.length()) + TRUNCATED;
    }
}
