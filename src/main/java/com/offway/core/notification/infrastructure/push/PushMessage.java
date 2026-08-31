package com.offway.core.notification.infrastructure.push;

import com.offway.core.notification.domain.NotificationType;
import java.util.Objects;

/**
 * 한 기기에 실어 보낼 내용(#357).
 *
 * <p><b>왜 인자를 늘리지 않고 묶었나.</b> {@link PushSender#send} 는 원래 종류와 코스 id 둘만 받았는데,
 * 여기에 알림 id 와 배지까지 붙으면 <b>비슷하게 생긴 nullable 인자 넷</b>이 나란히 선다. 그 자리에서는
 * 둘을 맞바꿔도 컴파일이 통과하고, 잘못 실린 값은 실기기에서만 드러난다.
 *
 * <p>문구는 여기 없다. 배너에 뭐라고 뜨는지는 {@link NotificationType} 이 알고 있고 어댑터가 거기서
 * 읽는다(#355) — 호출부가 문구를 들고 다니면 같은 말이 여러 곳에 생긴다.
 *
 * @param type 알림 종류 — 앱이 이동할 곳을 정하는 키이자 배너 문구의 출처
 * @param courseId 누르면 이동할 코스. 없는 알림도 있어 null 을 허용한다
 * @param notificationId 배너를 눌러 들어온 앱이 읽음 처리할 대상
 * @param badge 앱 아이콘에 그릴 안 읽은 개수. <b>모르면 null</b> 이고, 그때는 싣지 않아 앱이 직전 값을
 *     그대로 둔다 — 세는 데 실패했다고 0 을 보내면 안 읽은 알림이 있는데 배지가 지워진다
 */
public record PushMessage(NotificationType type, Long courseId, Long notificationId, Integer badge) {

    public PushMessage {
        Objects.requireNonNull(type, "알림 종류는 null 일 수 없습니다.");
        Objects.requireNonNull(notificationId, "알림 id 는 null 일 수 없습니다.");
    }
}
