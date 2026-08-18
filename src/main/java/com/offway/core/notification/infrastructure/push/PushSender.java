package com.offway.core.notification.infrastructure.push;

import com.offway.core.notification.domain.NotificationType;

/**
 * 푸시 한 건을 보내는 port(#270). 구현은 {@code FcmPushSender}.
 *
 * <p><b>문구를 받지 않는다.</b> 표시 문구는 앱이 소유한다(#263) — 종류와 코스 ID 만 실은 data 메시지를
 * 보내고 화면은 앱이 만든다. 문구를 서버가 실어 보내면 이미 발송된 알림은 영영 옛 문구로 남는다.
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
     * @param type 알림 종류 — 앱이 아이콘·문구를 맞추는 키
     * @param courseId 누르면 이동할 코스. 없으면 null
     */
    PushResult send(String token, NotificationType type, Long courseId);
}
