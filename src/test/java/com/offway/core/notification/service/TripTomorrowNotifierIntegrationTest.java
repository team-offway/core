package com.offway.core.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.Density;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.itinerary.domain.SlotDisplay;
import com.offway.core.itinerary.domain.SlotKind;
import com.offway.core.itinerary.domain.TimeOfDay;
import com.offway.core.itinerary.repository.CourseRepository;
import com.offway.core.notification.domain.NotificationType;
import com.offway.core.notification.repository.NotificationRepository;
import com.offway.core.transport.domain.TransportMode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * 여행 전날 알림을 만드는 배치(#269).
 *
 * <p>여기서 보는 것은 "DB 상태 + 결정" 이다 — 내일 떠나는 코스만 고르는가, 두 번 돌아도 알림이 하나인가.
 * 그 판정이 DB 제약에 얹혀 있어 단위로는 확인할 수 없다.
 *
 * <p><b>소유자도 날짜도 테스트마다 다르게 쓴다.</b> 이 클래스는 DB 를 롤백하지 않아(컨텍스트를 공유하는
 * 다른 통합 테스트와 같다) 잔여 상태가 새어 든다. 소유자만 갈라서는 부족한데, <b>이 배치는 소유자를
 * 가리지 않고 그 날짜의 코스를 전부 훑기 때문이다</b> — 다른 테스트가 우연히 같은 날짜의 코스를 남기면
 * 생성 건수가 어긋난다. 그래서 다른 어떤 테스트도 쓰지 않을 먼 미래 날짜를 시나리오마다 따로 쓴다.
 */
@SpringBootTest
class TripTomorrowNotifierIntegrationTest {

    @Autowired
    private TripTomorrowNotifier notifier;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void 내일_떠나는_코스의_주인에게_알림을_만든다() {
        String owner = "guest-269-tomorrow";
        LocalDate today = LocalDate.of(2099, 3, 1);
        Course course = saveCourse(owner, today.plusDays(1));

        int created = notifier.notifyTripsStartingTomorrow(today);

        assertEquals(1, created);
        assertEquals(1, ownedNotifications(owner).getTotalElements());
        assertEquals(NotificationType.TRIP_TOMORROW, ownedNotifications(owner).getContent().get(0).getType());
        assertEquals(course.getId(), ownedNotifications(owner).getContent().get(0).course().orElseThrow());
    }

    @Test
    void 오늘이나_모레_떠나는_코스는_대상이_아니다() {
        // "전날" 이 아니면 알릴 이유가 없다. 오늘 떠나는 사람에게 내일 떠난다고 알리면 틀린 말이다.
        String owner = "guest-269-other-days";
        LocalDate today = LocalDate.of(2099, 3, 10);
        saveCourse(owner, today);
        saveCourse(owner, today.plusDays(2));

        int created = notifier.notifyTripsStartingTomorrow(today);

        assertEquals(0, created);
        assertEquals(0, ownedNotifications(owner).getTotalElements());
    }

    @Test
    void 두_번_돌아도_같은_코스에_알림은_하나다() {
        // 배치가 재실행되거나 재배포로 주기가 다시 시작돼도 같은 알림이 겹치면 안 된다.
        String owner = "guest-269-rerun";
        LocalDate today = LocalDate.of(2099, 3, 20);
        saveCourse(owner, today.plusDays(1));

        int first = notifier.notifyTripsStartingTomorrow(today);
        int second = notifier.notifyTripsStartingTomorrow(today);

        assertEquals(1, first);
        assertEquals(0, second, "두 번째 실행은 새로 만든 것이 없어야 한다");
        assertEquals(1, ownedNotifications(owner).getTotalElements());
    }

    @Test
    void 같은_사람이_내일_떠나는_코스를_둘_저장했으면_둘_다_알린다() {
        // 코스마다 짐이 다르다. 한 건으로 합치면 어느 코스인지 알 수 없어 눌러 갈 곳이 정해지지 않는다.
        String owner = "guest-269-two-courses";
        LocalDate today = LocalDate.of(2099, 3, 25);
        saveCourse(owner, today.plusDays(1));
        saveCourse(owner, today.plusDays(1));

        int created = notifier.notifyTripsStartingTomorrow(today);

        assertEquals(2, created);
        assertEquals(2, ownedNotifications(owner).getTotalElements());
    }

    @Test
    void 대상이_없으면_아무것도_만들지_않는다() {
        assertTrue(notifier.notifyTripsStartingTomorrow(LocalDate.of(2099, 6, 1)) == 0);
    }

    /** 코스는 하루 이상이어야 성립하므로 최소 형태(1일 1슬롯)로 만든다. 이 테스트가 보는 것은 날짜뿐이다. */
    private Course saveCourse(String owner, LocalDate travelDate) {
        Slot slot = Slot.of(1, TimeOfDay.MORNING, SlotKind.SIGHT, "c1", "장소1", 37.50, 128.60, 0,
                new SlotDisplay(null, null, null, null));
        return courseRepository.save(Course.ownedBy(
                owner, 1L, Density.RELAXED, TransportMode.CAR,
                List.of(DaySchedule.of(1, List.of(slot))), travelDate, 1, null));
    }

    private Page<com.offway.core.notification.domain.Notification> ownedNotifications(String owner) {
        return notificationRepository.findByOwner(owner, PageRequest.of(0, 10));
    }
}
