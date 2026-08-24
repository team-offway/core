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
import com.offway.core.leave.domain.StartDayLeave;
import com.offway.core.notification.domain.Notification;
import com.offway.core.notification.domain.NotificationType;
import com.offway.core.notification.repository.NotificationRepository;
import com.offway.core.transport.domain.TransportMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * 여행 전날 알림을 만드는 배치(#269).
 *
 * <p>여기서 보는 것은 "DB 상태 + 결정" 이다 — 내일 떠나는 코스만 고르는가, 두 번 돌아도 알림이 하나인가,
 * 주인 없는 코스를 건너뛰는가. 그 판정이 DB 제약과 컬럼 상태에 얹혀 있어 단위로는 확인할 수 없다.
 *
 * <p><b>이 경로가 {@code saveIfAbsent} 의 유일한 실사용처다</b>(#280). 그 문장만 native 라 소유자를
 * {@code UUID_TO_BIN} 으로 바인딩하는데, 조회는 Hibernate 의 {@code @JdbcTypeCode(BINARY)} 매핑을 탄다.
 * 두 경로의 바이트가 어긋나면 <b>알림은 만들어지는데 아무에게도 안 보인다</b> — 예외도 로그도 남지 않는
 * 조용한 실패다. 그래서 아래 단언은 "몇 건 만들었나" 로 끝내지 않고 <b>JPA 로 되읽어</b> 확인한다.
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
        UUID owner = UUID.randomUUID();
        LocalDate today = LocalDate.of(2099, 3, 1);
        Course course = saveOwnedCourse(owner, today.plusDays(1));

        int created = notifier.notifyTripsStartingTomorrow(today);

        assertEquals(1, created);
        // native INSERT 로 넣은 소유자를 JPA 조회가 그대로 찾아야 한다 — 두 경로의 BINARY(16) 표현이
        // 같다는 확인이 여기서 실행으로 이뤄진다. 어긋나면 이 단언이 0 으로 떨어진다.
        Page<Notification> mine = ownedNotifications(owner);
        assertEquals(1, mine.getTotalElements());
        assertEquals(NotificationType.TRIP_TOMORROW, mine.getContent().get(0).getType());
        assertEquals(course.getId(), mine.getContent().get(0).course().orElseThrow());
        assertEquals(owner, mine.getContent().get(0).getUserId());
    }

    @Test
    void 오늘이나_모레_떠나는_코스는_대상이_아니다() {
        // "전날" 이 아니면 알릴 이유가 없다. 오늘 떠나는 사람에게 내일 떠난다고 알리면 틀린 말이다.
        UUID owner = UUID.randomUUID();
        LocalDate today = LocalDate.of(2099, 3, 10);
        saveOwnedCourse(owner, today);
        saveOwnedCourse(owner, today.plusDays(2));

        int created = notifier.notifyTripsStartingTomorrow(today);

        assertEquals(0, created);
        assertEquals(0, ownedNotifications(owner).getTotalElements());
    }

    @Test
    void 두_번_돌아도_같은_코스에_알림은_하나다() {
        // 배치가 재실행되거나 재배포로 주기가 다시 시작돼도 같은 알림이 겹치면 안 된다.
        UUID owner = UUID.randomUUID();
        LocalDate today = LocalDate.of(2099, 3, 20);
        saveOwnedCourse(owner, today.plusDays(1));

        int first = notifier.notifyTripsStartingTomorrow(today);
        int second = notifier.notifyTripsStartingTomorrow(today);

        assertEquals(1, first);
        assertEquals(0, second, "두 번째 실행은 새로 만든 것이 없어야 한다");
        assertEquals(1, ownedNotifications(owner).getTotalElements());
    }

    @Test
    void 같은_사람이_내일_떠나는_코스를_둘_저장했으면_둘_다_알린다() {
        // 코스마다 짐이 다르다. 한 건으로 합치면 어느 코스인지 알 수 없어 눌러 갈 곳이 정해지지 않는다.
        UUID owner = UUID.randomUUID();
        LocalDate today = LocalDate.of(2099, 3, 25);
        saveOwnedCourse(owner, today.plusDays(1));
        saveOwnedCourse(owner, today.plusDays(1));

        int created = notifier.notifyTripsStartingTomorrow(today);

        assertEquals(2, created);
        assertEquals(2, ownedNotifications(owner).getTotalElements());
    }

    /**
     * 주인 없는 코스는 <b>건너뛴다</b> — 실패가 아니다.
     *
     * <p>담지 않고 링크만 만든 공유 전용 코스는 소유자를 두지 않는 것이 설계다({@code Course.sharedOnly},
     * #261). 알릴 사람이 없으므로 알림을 만들 수 없고, 그렇다고 배치가 멈춰서도 안 된다 — <b>같은 날짜의
     * 다른 코스는 그대로 알림을 받아야 한다.</b> 이 분기가 없으면 소유자 컬럼이 null 인 채 알림이 만들어져
     * {@code user_id NOT NULL} 에 걸리고, 그 예외가 그날 대상 전체를 흔든다.
     */
    @Test
    void 주인_없는_공유_전용_코스는_건너뛰고_나머지는_알린다() {
        UUID owner = UUID.randomUUID();
        LocalDate today = LocalDate.of(2099, 5, 5);
        saveSharedOnlyCourse(today.plusDays(1));
        saveOwnedCourse(owner, today.plusDays(1));

        int created = notifier.notifyTripsStartingTomorrow(today);

        assertEquals(1, created, "주인 있는 코스 하나만 알림이 만들어져야 한다");
        assertEquals(1, ownedNotifications(owner).getTotalElements());
    }

    @Test
    void 주인_없는_코스만_있으면_아무_알림도_안_만든다() {
        LocalDate today = LocalDate.of(2099, 5, 12);
        saveSharedOnlyCourse(today.plusDays(1));

        assertEquals(0, notifier.notifyTripsStartingTomorrow(today));
    }

    /**
     * 이미 만들어진 것이 섞인 배치에서도 <b>새 대상은 만들어진다</b>.
     *
     * <p>배치를 한 트랜잭션으로 묶으면 중간 한 건이 걸릴 때 그날 대상 전원이 알림을 못 받는다. 이 배치는
     * 하루에 한 번이라 다음 실행은 여행이 이미 시작된 뒤다 — 한 건의 사정이 나머지에 번지면 안 된다.
     */
    @Test
    void 이미_있는_알림이_섞여_있어도_새_대상은_만든다() {
        LocalDate today = LocalDate.of(2099, 4, 2);
        UUID owner = UUID.randomUUID();
        saveOwnedCourse(owner, today.plusDays(1));
        saveOwnedCourse(owner, today.plusDays(1));
        assertEquals(2, notifier.notifyTripsStartingTomorrow(today));

        // 앞선 실행 뒤에 코스가 하나 더 저장됐다 — 이제 배치 대상에 "이미 있음" 과 "새것" 이 섞인다.
        saveOwnedCourse(owner, today.plusDays(1));

        int created = notifier.notifyTripsStartingTomorrow(today);

        assertEquals(1, created, "이미 있는 둘은 건너뛰고 새 하나만 만들어야 한다");
        assertEquals(3, ownedNotifications(owner).getTotalElements());
    }

    @Test
    void 대상이_없으면_아무것도_만들지_않는다() {
        assertTrue(notifier.notifyTripsStartingTomorrow(LocalDate.of(2099, 6, 1)) == 0);
    }

    /** 코스는 하루 이상이어야 성립하므로 최소 형태(1일 1슬롯)로 만든다. 이 테스트가 보는 것은 날짜와 주인뿐이다. */
    private Course saveOwnedCourse(UUID owner, LocalDate travelDate) {
        return courseRepository.save(Course.ownedBy(
                owner, 1L, Density.RELAXED, TransportMode.CAR,
                List.of(DaySchedule.of(1, List.of(minimalSlot()))), travelDate, 1, null, StartDayLeave.DEFAULT));
    }

    /** 담지 않고 공유 링크만 만든 코스 — 주인이 없는 것이 설계다(#261). */
    private Course saveSharedOnlyCourse(LocalDate travelDate) {
        return courseRepository.save(Course.sharedOnly(
                1L, Density.RELAXED, TransportMode.CAR,
                List.of(DaySchedule.of(1, List.of(minimalSlot()))), travelDate, 1, null, StartDayLeave.DEFAULT));
    }

    private static Slot minimalSlot() {
        return Slot.of(1, TimeOfDay.MORNING, SlotKind.SIGHT, "c1", "장소1", 37.50, 128.60, 0,
                new SlotDisplay(null, null, null, null));
    }

    private Page<Notification> ownedNotifications(UUID owner) {
        return notificationRepository.findByOwner(owner, PageRequest.of(0, 10));
    }
}
