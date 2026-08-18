package com.offway.core.notification.infrastructure.push;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.offway.core.common.logging.RootCause;
import com.offway.core.notification.domain.NotificationType;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * FCM 발송 adapter(#270).
 *
 * <p><b>서비스 계정 키가 없으면 비활성으로 뜬다.</b> 키를 부팅 조건으로 만들면 로컬·CI 가 전부 막힌다 —
 * 키 없이도 부팅되어야 한다는 것이 이 프로젝트의 불변식이다(CLAUDE.md 로컬 실행성). 그때 보낼 수단이
 * 없다는 사실은 {@link PushResult#DISABLED} 로 드러나고, 실패로 세지 않는다.
 *
 * <p><b>data 메시지만 보낸다.</b> notification 필드를 실으면 앱이 백그라운드일 때 시스템이 알림을 대신
 * 그려 문구가 서버에 굳는다. 표시는 앱이 소유한다(#263).
 */
@Slf4j
@Component
public class FcmPushSender implements PushSender {

    /** 앱이 알림 종류를 읽는 키. 앱과 맞춘 계약이라 값을 바꾸면 앱이 못 읽는다. */
    private static final String DATA_KEY_TYPE = "type";

    /** 누르면 이동할 코스. 없는 알림도 있어 있을 때만 싣는다. */
    private static final String DATA_KEY_COURSE_ID = "courseId";

    private final FirebaseMessaging messaging;

    /**
     * @param messaging 키가 없으면 빈 자체가 없다 — {@link ObjectProvider} 로 받는 이유다. 생성자에서
     *     바로 받으면 빈이 없을 때 주입이 실패해 부팅이 막히는데, 그것이 이 어댑터가 피하려는 상황이다
     */
    public FcmPushSender(ObjectProvider<FirebaseMessaging> messaging) {
        this.messaging = messaging.getIfAvailable();
        if (this.messaging == null) {
            log.warn("FCM 서비스 계정 키가 없어 푸시 발송을 비활성으로 시작합니다 — 알림은 앱 안 목록에만 쌓입니다");
        }
    }

    @Override
    public PushResult send(String token, NotificationType type, Long courseId) {
        if (messaging == null) {
            return PushResult.DISABLED;
        }
        try {
            messaging.send(Message.builder()
                    .setToken(token)
                    .putAllData(payload(type, courseId))
                    .build());
            return PushResult.SENT;
        } catch (FirebaseMessagingException e) {
            return classify(e);
        }
    }

    /**
     * 죽은 토큰과 일시 실패를 가른다.
     *
     * <p>{@code UNREGISTERED} 는 앱이 지워졌거나 재설치된 것이고, {@code INVALID_ARGUMENT} 는 토큰 형식이
     * 애초에 틀린 것이다. 둘 다 다시 보내도 영영 실패하므로 지울 대상으로 본다. 그 외(할당량·서버 오류·
     * 네트워크)는 다음에 성공할 수 있어 남긴다.
     */
    private PushResult classify(FirebaseMessagingException e) {
        MessagingErrorCode code = e.getMessagingErrorCode();
        if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
            log.debug("푸시 대상 토큰이 유효하지 않습니다 — 정리 대상 code={}", code);
            return PushResult.UNREGISTERED;
        }
        // 토큰은 로그에 남기지 않는다(그 자체로 그 기기에 알림을 보낼 수 있는 자격이다).
        log.warn("FCM 발송 실패 code={} cause={}", code, RootCause.of(e));
        return PushResult.FAILED;
    }

    private Map<String, String> payload(NotificationType type, Long courseId) {
        Map<String, String> data = new HashMap<>();
        data.put(DATA_KEY_TYPE, type.name());
        if (courseId != null) {
            data.put(DATA_KEY_COURSE_ID, String.valueOf(courseId));
        }
        return data;
    }
}
