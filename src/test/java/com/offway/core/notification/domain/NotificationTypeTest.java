package com.offway.core.notification.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 배너 문구가 종류마다 갖춰져 있는지(#355).
 *
 * <p><b>전수로 도는 이유</b> — 종류를 새로 더할 때 문구를 빠뜨리면, 그 알림만 잠금화면에서 빈 배너로
 * 뜬다. 컴파일은 통과한다(생성자가 받으니 값은 넣지만 빈 문자열일 수 있다). 여기서 막는다.
 */
class NotificationTypeTest {

    @ParameterizedTest
    @EnumSource(NotificationType.class)
    void 모든_종류가_배너_문구를_갖는다(NotificationType type) {
        assertFalse(type.bannerTitle("정선").isBlank(), type + " 의 배너 제목이 비었습니다");
        assertFalse(type.bannerBody().isBlank(), type + " 의 배너 본문이 비었습니다");
    }

    /**
     * 여행지를 못 찾는 코스가 있다 — 지워졌거나 지역 조회가 실패한 경우다. 그때 제목이 빈칸이나
     * {@code null} 로 시작하면 잠금화면에 그대로 보인다.
     */
    @ParameterizedTest
    @EnumSource(NotificationType.class)
    void 여행지가_없어도_제목이_멀쩡하다(NotificationType type) {
        for (String destination : new String[] {null, "", "   "}) {
            String title = type.bannerTitle(destination);
            assertFalse(title.isBlank(), type + " 의 제목이 비었습니다 여행지=" + destination);
            assertFalse(title.contains("null"), type + " 의 제목에 null 이 들어갔습니다: " + title);
            assertEquals(title.strip(), title, type + " 의 제목 앞뒤에 공백이 남았습니다: [" + title + "]");
        }
    }

    /**
     * 앱은 두 줄로 그리지만 배너는 폭이 좁아 어차피 한 줄로 줄어든다. 개행이 남으면 그 자리가 공백으로
     * 벌어져 문장 중간이 이상하게 끊긴 것처럼 보인다.
     */
    @ParameterizedTest
    @EnumSource(NotificationType.class)
    void 배너_본문에_개행을_넣지_않는다(NotificationType type) {
        assertFalse(type.bannerBody().contains("\n"), type + " 의 배너 본문에 개행이 있습니다");
    }

    /**
     * 문구는 앱이 알림함에 쓰는 것과 같은 말이어야 한다 — 배너와 목록이 다르게 말하면 사용자는 두 개의
     * 알림으로 읽는다. 프론트가 이슈에 적어 준 문구 그대로다(#355).
     */
    @Test
    void 앱이_알림함에_쓰는_문구와_같다() {
        assertEquals("내일은 여행을 떠나는 날이에요. 짐은 다 챙기셨나요?", NotificationType.TRIP_TOMORROW.bannerBody());
        assertEquals("연차를 사용했다면 기록해주세요", NotificationType.TRIP_AFTER.bannerBody());
    }

    /**
     * 여행이 끝난 뒤 묻는 알림은 <b>어느 여행인지가 곧 그 알림의 정체</b>라, 제목에 여행지를 세운다.
     * 짧은 이름을 쓰는 것은 알림함이 그렇게 내리고 있기 때문이다(#356).
     */
    @Test
    void 여행_후_알림은_제목에_여행지를_세운다() {
        assertEquals("정선 여행 다녀오셨나요?", NotificationType.TRIP_AFTER.bannerTitle("정선"));
        assertEquals("여행 다녀오셨나요?", NotificationType.TRIP_AFTER.bannerTitle(null));
    }

    /**
     * 여행지를 말할 자리가 없는 종류는 여행지를 받아도 쓰지 않는다 — 전날 알림의 제목에 여행지를 끼우면
     * 앱이 알림함에 쓰는 문장과 어긋난다.
     */
    @Test
    void 여행지를_쓰지_않는_종류는_받아도_무시한다() {
        assertEquals(NotificationType.TRIP_TOMORROW.bannerTitle(null),
                NotificationType.TRIP_TOMORROW.bannerTitle("정선"));
    }

    /** 상수 이름은 앱과 맞춘 계약이다 — 바꾸면 앱이 아이콘·이동 경로를 못 고른다. */
    @Test
    void 앱이_읽는_상수_이름이_그대로다() {
        assertTrue(java.util.Arrays.stream(NotificationType.values())
                .map(Enum::name)
                .toList()
                .containsAll(java.util.List.of("TRIP_TOMORROW", "TRIP_AFTER")));
    }
}
