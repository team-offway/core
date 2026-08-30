package com.offway.core.notification.infrastructure.push;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
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
 * <p><b>data 와 notification 을 함께 보낸다(#355).</b> 예전에는 data 만 실었는데, 그러면 iOS 가 silent push
 * 로 취급해 <b>백그라운드·종료 상태에서 아무것도 그리지 않는다</b> — 전달 자체가 지연되거나 버려지기도
 * 한다. 그런데 이 알림들은 전날 20시·다음 날 20시 배치라 정확히 그 상태에서 필요하다. 붙여 놓고 안 보이는
 * 상태였다.
 *
 * <p><b>data 는 그대로 둔다.</b> 앱이 {@code type}·{@code courseId} 로 이동할 곳을 정하므로, 빼면 배너를
 * 눌러도 엉뚱한 데로 간다.
 *
 * <p>문구가 서버에 굳는 걱정은 알림함에서만 성립한다 — 근거는 {@link NotificationType} 에 있다.
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
            messaging.send(message(token, type, courseId));
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

    /**
     * 보낼 메시지 한 건 — <b>테스트가 닿을 수 있게</b> 발송과 조립을 나눠 둔다(package-private).
     *
     * <p>합쳐 두면 "notification 을 싣는가" 를 확인할 방법이 실기기밖에 없다. 실제로 그래서 이 결함이
     * 12일 동안 아무 신호 없이 살아 있었다 — 발송은 성공(`SENT`)으로 집계되고 있었다.
     */
    Message message(String token, NotificationType type, Long courseId) {
        return Message.builder()
                .setToken(token)
                .putAllData(payload(type, courseId))
                .setNotification(banner(type))
                .build();
    }

    /**
     * 잠금화면에 그려질 알림. 문구는 {@link NotificationType} 이 소유한다 — 여기서 조립하면 같은 말이
     * 어댑터와 도메인 두 곳에 생긴다.
     */
    private static Notification banner(NotificationType type) {
        return Notification.builder()
                .setTitle(type.bannerTitle())
                .setBody(type.bannerBody())
                .build();
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
