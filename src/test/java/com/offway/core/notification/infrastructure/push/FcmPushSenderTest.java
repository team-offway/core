package com.offway.core.notification.infrastructure.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.firebase.messaging.FcmMessages;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.offway.core.notification.domain.NotificationType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 우리가 FCM 에 <b>무엇을 보내는가</b>(#355).
 *
 * <p>이 결함은 12일 동안 아무 신호 없이 살아 있었다 — 발송은 {@link PushResult#SENT} 로 성공했고 CI 도
 * 초록이었다. <b>보내는 내용 자체를 아무도 안 봤기 때문</b>이다. 여기서 그 자리를 만든다.
 *
 * <p>키가 없는 환경을 그대로 쓴다(FCM 을 부르지 않는다). 조립과 발송이 나뉘어 있어 조립만 따로 확인할 수
 * 있고, 그래서 이 테스트는 네트워크도 서비스 계정 키도 필요 없다.
 */
class FcmPushSenderTest {

    private static final String TOKEN = "device-token";

    /** 앱이 이동할 코스를 읽는 키 — {@code FcmPushSender} 와 맞춘 <b>앱과의 계약</b>이다. */
    private static final String DATA_KEY_TYPE = "type";

    private static final String DATA_KEY_COURSE_ID = "courseId";

    /** 코스가 있는 알림에 쓰는 값. 무엇이든 되지만 응답에서 문자열로 바뀌는 것을 함께 본다. */
    private static final long COURSE_ID = 7L;

    /** 키 없는 환경을 만들 때 부르면 안 되는 자리에 남기는 말. */
    private static final String NEVER_CALLED = "키 없는 환경에서는 부르지 않는다";

    /** 키가 없는 환경 — 빈 자체가 없어 {@code getIfAvailable()} 이 null 을 준다. */
    private static FcmPushSender senderWithoutKey() {
        return new FcmPushSender(new ObjectProvider<FirebaseMessaging>() {
            @Override
            public FirebaseMessaging getObject(Object... args) {
                throw new UnsupportedOperationException(NEVER_CALLED);
            }

            @Override
            public FirebaseMessaging getIfAvailable() {
                return null;
            }

            @Override
            public FirebaseMessaging getIfUnique() {
                return null;
            }

            @Override
            public FirebaseMessaging getObject() {
                throw new UnsupportedOperationException(NEVER_CALLED);
            }
        });
    }

    /**
     * <b>이 한 줄이 이 PR 의 전부다.</b> {@code notification} 이 없으면 iOS 가 silent push 로 취급해
     * 백그라운드·종료 상태에서 아무것도 그리지 않는다 — 그런데 이 알림들은 정확히 그 상태에서 필요하다.
     */
    @ParameterizedTest
    @EnumSource(NotificationType.class)
    void 모든_종류가_배너로_그려질_알림을_싣는다(NotificationType type) {
        Message message = senderWithoutKey().message(TOKEN, type, COURSE_ID);

        assertNotNull(FcmMessages.notificationOf(message), type + " 가 notification 없이 나갑니다(배너가 안 뜬다)");
    }

    /** 실제 전송 payload 에서 제목·본문을 읽는다 — {@code Notification} 은 접근자가 하나도 없다. */
    @ParameterizedTest
    @EnumSource(NotificationType.class)
    void 배너에_종류에_맞는_문구가_실린다(NotificationType type) {
        Message message = senderWithoutKey().message(TOKEN, type, COURSE_ID);

        String payload = FcmMessages.transportJsonOf(message);

        assertTrue(payload.contains(type.bannerTitle()), payload);
        assertTrue(payload.contains(type.bannerBody()), payload);
    }

    /**
     * <b>data 를 빼지 않는다.</b> 앱이 {@code type}·{@code courseId} 로 이동할 곳을 정하므로, 빠지면 배너를
     * 눌러도 엉뚱한 데로 간다 — 안 뜨는 것을 고치다가 잘못 가는 것을 만들면 안 된다.
     */
    @Test
    void 배너를_붙여도_앱이_읽는_data_는_그대로다() {
        Message message = senderWithoutKey().message(TOKEN, NotificationType.TRIP_AFTER, COURSE_ID);

        Map<String, String> data = FcmMessages.dataOf(message);

        // **키는 상수로, 값은 리터럴로.** 키는 이 파일 안에서 되풀이되지만, 값은 앱이 읽는 이름 그 자체라
        // enum 을 참조하면 상수명을 바꿔도 테스트가 따라가 버린다 — 앱은 못 따라간다.
        assertEquals("TRIP_AFTER", data.get(DATA_KEY_TYPE));
        assertEquals(String.valueOf(COURSE_ID), data.get(DATA_KEY_COURSE_ID));
    }

    /** 코스가 없는 알림도 있다 — 그때는 키 자체를 싣지 않아 앱이 "이동 없음" 으로 읽는다. */
    @Test
    void 코스가_없으면_courseId_를_싣지_않는다() {
        Message message = senderWithoutKey().message(TOKEN, NotificationType.TRIP_AFTER, null);

        assertNull(FcmMessages.dataOf(message).get(DATA_KEY_COURSE_ID));
    }

    /** 키가 없어도 부팅·발송 호출이 깨지지 않는다(로컬 실행성). 실패로 세지 않는 결과로 돌려준다. */
    @Test
    void 키가_없으면_발송하지_않고_비활성으로_돌려준다() {
        assertEquals(PushResult.DISABLED, senderWithoutKey().send(TOKEN, NotificationType.TRIP_TOMORROW, COURSE_ID));
    }
}
