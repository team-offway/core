package com.offway.core.notification.infrastructure.push;

import com.offway.core.notification.domain.NotificationType;

/**
 * 푸시 한 건을 보내는 port(#270). 구현은 {@code FcmPushSender}.
 *
 * <p><b>문구를 인자로 받지 않는다.</b> 호출부가 문구를 들고 다니면 같은 말이 여러 곳에 생긴다 — 배너에
 * 뭐라고 뜨는지는 {@link NotificationType} 이 알고 있고, 어댑터가 거기서 읽는다(#355).
 *
 * <p>도메인이 이 인터페이스에만 의존하고 Firebase 세부는 adapter 에 가둔다. 키가 없는 환경에서는
 * 비활성 구현이 뜨므로 호출부는 키 유무를 몰라도 된다.
 */
public interface PushSender {

    /**
     * 한 기기에 보낸다.
     *
     * <p><b>예외를 던지지 않는다.</b> 발송은 여러 기기를 도는 팬아웃이라, 한 건의 실패가 나머지를 막으면
     * 안 된다. 실패도 결과값으로 돌려 호출자가 집계·정리에 쓰게 한다.
     *
     * @param token 기기 토큰
     * @param type 알림 종류 — 앱이 아이콘·문구를 맞추는 키이자, 배너 문구의 출처
     * @param courseId 누르면 이동할 코스. 없으면 null
     */
    PushResult send(String token, NotificationType type, Long courseId);
}
