package com.offway.core.liveactivity.infrastructure.apns;

/**
 * 잠금화면 카드 한 건을 보내는 port(#575). 구현은 {@link ApnsLiveActivitySender}.
 *
 * <h2>{@code PushSender} 를 왜 안 쓰나</h2>
 *
 * <p>기존 푸시 port 는 FCM 위에 서 있고, 계약도 {@code NotificationType}(배너 문구)·배지 숫자에 맞춰져
 * 있다. <b>Live Activity 는 FCM 이 중계하지 않는다</b> — APNs 를 직접 불러야 하고, 실어 보내는 것도
 * 문구가 아니라 잠금화면 상태값이다. 한 port 에 얹으면 양쪽 다 nullable 필드로 지저분해지고, 무엇보다
 * FCM 이 못 보내는 것을 보내는 것처럼 보이는 계약이 된다.
 *
 * <p>도메인이 이 인터페이스에만 의존하고 APNs 세부(HTTP/2·JWT·토픽)는 adapter 에 가둔다. 키가 없는
 * 환경에서는 {@link ApnsResult#DISABLED} 가 돌아오므로 호출부는 키 유무를 몰라도 된다.
 */
public interface LiveActivitySender {

    /**
     * 한 카드에 보낸다.
     *
     * <p><b>예외를 던지지 않는다.</b> 발송은 여러 카드를 도는 팬아웃이라, 한 건의 실패가 나머지를 막으면
     * 안 된다. 실패도 결과값으로 돌려 호출자가 집계·정리에 쓰게 한다.
     *
     * @param token 그 카드의 Live Activity push token
     * @param push 갱신할 값 또는 종료
     */
    ApnsResult send(String token, LiveActivityPush push);
}
