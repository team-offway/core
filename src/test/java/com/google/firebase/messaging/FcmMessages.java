package com.google.firebase.messaging;

import com.google.api.client.json.gson.GsonFactory;
import java.io.IOException;
import java.util.Map;

/**
 * FCM {@code Message} 의 내용을 테스트가 읽게 해 주는 창(#355).
 *
 * <p><b>왜 남의 패키지에 두나.</b> Firebase Admin SDK 는 {@code Message.getNotification()}·{@code getData()}
 * 를 <b>package-private</b> 로 두고 있다. 밖에서는 우리가 무엇을 보내는지 확인할 방법이 없어, "notification
 * 을 실었는가" 를 잠글 수 있는 자리가 실기기 확인밖에 남지 않는다.
 *
 * <p>그게 이 결함이 12일 동안 살아 있던 이유다 — 발송은 성공으로 집계됐고 CI 도 초록이었다. 회귀를
 * 잡으려면 이 한 칸이 필요하다.
 *
 * <p>리플렉션 대신 같은 패키지에 두는 것을 골랐다. 리플렉션은 SDK 가 이름을 바꾸면 <b>런타임에</b>
 * 깨지지만, 이 방식은 컴파일이 먼저 알려준다.
 */
public final class FcmMessages {

    private FcmMessages() {
    }

    /**
     * FCM 으로 <b>실제로 나가는 JSON</b>.
     *
     * <p>{@code Notification} 은 접근자가 하나도 없고 필드가 전부 private 이라, 제목·본문을 읽으려면 이
     * 길밖에 없다. 대신 얻는 것이 있다 — SDK 가 전송에 쓰는 바로 그 직렬화기({@code @Key} 기반)를 통과한
     * 결과라, 우리 객체가 아니라 <b>서버에 도착할 모양</b>을 본다.
     */
    public static String transportJsonOf(Message message) {
        try {
            return GsonFactory.getDefaultInstance().toString(message.wrapForTransport(false));
        } catch (IOException e) {
            throw new IllegalStateException("FCM 메시지를 직렬화하지 못했습니다", e);
        }
    }

    public static Map<String, String> dataOf(Message message) {
        return message.getData();
    }

    public static Notification notificationOf(Message message) {
        return message.getNotification();
    }
}
