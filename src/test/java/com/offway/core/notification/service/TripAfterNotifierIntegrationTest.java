package com.offway.core.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.batch.repository.BatchRunRepository;
import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.Density;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.itinerary.domain.SlotDisplay;
import com.offway.core.itinerary.domain.SlotKind;
import com.offway.core.itinerary.domain.TimeOfDay;
import com.offway.core.itinerary.domain.TripOutcome;
import com.offway.core.itinerary.domain.VisitOutcome;
import com.offway.core.itinerary.repository.CourseRepository;
import com.offway.core.itinerary.repository.TripOutcomeRepository;
import com.offway.core.itinerary.service.CourseLeaveDeductionService;
import com.offway.core.leave.domain.StartDayLeave;
import com.offway.core.notification.domain.Notification;
import com.offway.core.notification.domain.NotificationType;
import com.offway.core.notification.repository.NotificationRepository;
import com.offway.core.transport.domain.TransportMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * 여행이 끝난 다음 날 "다녀오셨나요?" 를 알리는 배치(#302).
 *
 * <p>여기서 보는 것은 "DB 상태 + 결정" 이다 — 어제 끝난 것만 고르는가, 이미 답했거나 차감한 사람은 빼는가,
 * 두 번 돌아도 알림이 하나인가. 그 판정이 여러 테이블에 걸쳐 있어 단위로는 확인할 수 없다.
 *
 * <p><b>날짜를 시나리오마다 다르게 쓴다.</b> 이 클래스는 DB 를 롤백하지 않고(컨텍스트를 공유하는 다른 통합
 * 테스트와 같다), <b>이 배치는 소유자를 가리지 않고 그 날짜의 코스를 전부 훑는다</b> — 다른 테스트가 우연히
 * 같은 날짜의 코스를 남기면 생성 건수가 어긋난다. 그래서 다른 어떤 테스트도 쓰지 않을 먼 미래 날짜를
 * 시나리오마다 따로 쓴다.
 */
@SpringBootTest
class TripAfterNotifierIntegrationTest {

    @Autowired
    private TripAfterNotifier notifier;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private TripOutcomeRepository tripOutcomeRepository;

    @Autowired
    private CourseLeaveDeductionService leaveDeductionService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private BatchRunRepository batchRunRepository;

    @Test
    void 어제_끝난_여행의_주인에게_알림을_만든다() {
        UUID owner = UUID.randomUUID();
        LocalDate today = LocalDate.of(2099, 9, 2);
        Course course = saveCourse(owner, today.minusDays(1), 1);

        int created = notifier.notifyTripsEndedYesterday(today);

        assertEquals(1, created);
        Page<Notification> mine = ownedNotifications(owner);
        assertEquals(1, mine.getTotalElements());
        assertEquals(NotificationType.TRIP_AFTER, mine.getContent().get(0).getType());
        assertEquals(course.getId(), mine.getContent().get(0).course().orElseThrow());
    }

    /**
     * 여러 날짜 여행은 <b>시작일이 아니라 종료일</b>이 기준이다.
     *
     * <p>종료일은 컬럼이 아니라 {@code travel_date + travel_days - 1} 로 계산된다. 시작일로 잘못 잡으면
     * 2박 3일 여행에 <b>아직 여행 중일 때</b> "다녀오셨나요?" 가 간다.
     */
    @Test
    void 이박삼일_여행은_마지막_날_기준으로_알린다() {
        UUID owner = UUID.randomUUID();
        LocalDate today = LocalDate.of(2099, 9, 10);
        // 9/7 출발 3일 = 9/9 종료. today 가 9/10 이므로 어제 끝난 여행이다.
        saveCourse(owner, today.minusDays(3), 3);

        assertEquals(1, notifier.notifyTripsEndedYesterday(today));
        assertEquals(1, ownedNotifications(owner).getTotalElements());
    }

    /**
     * <b>1박 2일 — 운영에서 알림이 안 온 그 모양이다(#309).</b>
     *
     * <p>TestFlight 사용자의 횡성 여행이 8/19 출발 1박 2일(종료 8/20)이었는데 8/21 20:00 배치 뒤에도 알림함이
     * 비어 있었다. 홈 모달은 떠 있었으므로 "답하지 않았고 차감도 안 했다" 는 조건은 만족한 상태였다.
     *
     * <p>기간별로는 1일과 3일만 잠겨 있었다. <b>2일이 비어 있었다</b> — 범위 질의가
     * {@code endedOn - (MAX_TRAVEL_DAYS - 1)} 부터 훑으므로 2일짜리는 그 안쪽에 놓이는데, 안쪽이라고 해서
     * 자동으로 맞는 것은 아니다. 그 자리를 실제 날짜로 잠근다.
     */
    @Test
    void 일박이일_여행도_종료_다음_날_알린다() {
        UUID owner = UUID.randomUUID();
        // 운영 사례와 같은 날짜 모양: 8/19 출발 · 2일 · 8/20 종료 · 8/21 배치
        LocalDate today = LocalDate.of(2099, 8, 21);
        Course course = saveCourse(owner, LocalDate.of(2099, 8, 19), 2);

        int created = notifier.notifyTripsEndedYesterday(today);

        assertEquals(1, created, "1박 2일 여행이 대상에서 빠졌다");
        Page<Notification> mine = ownedNotifications(owner);
        assertEquals(1, mine.getTotalElements());
        assertEquals(course.getId(), mine.getContent().get(0).course().orElseThrow());
    }

    /**
     * 범위 질의의 <b>양 끝</b>이 열려 있는지 — 하루 어긋나면 그 기간의 여행만 조용히 빠진다.
     *
     * <p>후보를 {@code travel_date BETWEEN endedOn - 2 AND endedOn} 으로 좁혀 오므로, 상단은 종료일 당일에
     * 시작한 1일 여행이고 하단은 사흘 전에 시작한 3일 여행이다. 둘 다 경계 위에 정확히 놓인다.
     */
    @Test
    void 범위의_양_끝에_걸친_여행도_빠지지_않는다() {
        UUID owner = UUID.randomUUID();
        LocalDate today = LocalDate.of(2099, 8, 28);
        LocalDate endedOn = today.minusDays(1);
        saveCourse(owner, endedOn, 1);                  // 상단 — 종료일에 시작해 그날 끝난다
        saveCourse(owner, endedOn.minusDays(2), 3);     // 하단 — 가장 이른 시작일

        assertEquals(2, notifier.notifyTripsEndedYesterday(today), "경계에 놓인 여행이 빠졌다");
        assertEquals(2, ownedNotifications(owner).getTotalElements());
    }

    /**
     * 종료 당일에는 안 보낸다 — <b>아직 여행 중일 수 있다</b>.
     *
     * <p>그저께 끝난 것도 안 보낸다. 지난 미답 여행 <b>전부</b>를 대상으로 잡으면 첫 배포에 몇 달 전 여행까지
     * 한꺼번에 알림·푸시로 나간다.
     */
    @Test
    void 오늘_끝났거나_그저께_끝난_여행은_대상이_아니다() {
        UUID owner = UUID.randomUUID();
        LocalDate today = LocalDate.of(2099, 9, 20);
        saveCourse(owner, today, 1);
        saveCourse(owner, today.minusDays(2), 1);

        assertEquals(0, notifier.notifyTripsEndedYesterday(today));
        assertTrue(ownedNotifications(owner).isEmpty());
    }

    /**
     * <b>이미 답한 여행에는 보내지 않는다.</b>
     *
     * <p>보내면 눌러서 들어가도 모달이 안 떠 사용자가 헛걸음한다 — 알림이 있는데 아무 일도 안 일어나는 것이
     * 알림이 없는 것보다 나쁘다.
     */
    @Test
    void 이미_답한_여행에는_보내지_않는다() {
        UUID owner = UUID.randomUUID();
        LocalDate today = LocalDate.of(2099, 9, 25);
        Course answered = saveCourse(owner, today.minusDays(1), 1);
        Course notAnswered = saveCourse(owner, today.minusDays(1), 1);
        tripOutcomeRepository.save(
                TripOutcome.of(owner, answered.getId(), VisitOutcome.NOT_VISITED, today));

        int created = notifier.notifyTripsEndedYesterday(today);

        assertEquals(1, created, "답 안 한 것 하나만 만들어야 한다");
        assertEquals(
                notAnswered.getId(),
                ownedNotifications(owner).getContent().get(0).course().orElseThrow());
    }

    /**
     * <b>이미 차감한 여행에도 보내지 않는다.</b>
     *
     * <p>차감은 {@code VISITED} 로 답할 때만 생기므로(#288) 차감이 있다는 것은 곧 답이 있다는 뜻이다 — {@code pending} 이
     * 같은 이유로 걸러낸다. 조회 조건이 갈리면 모달이 안 뜨는 알림이 생긴다.
     */
    @Test
    void 이미_차감한_여행에는_보내지_않는다() {
        UUID owner = UUID.randomUUID();
        LocalDate today = LocalDate.of(2099, 10, 1);
        Course deducted = saveCourse(owner, today.minusDays(1), 1);
        leaveDeductionService.deduct(owner, deducted.getId(), StartDayLeave.DEFAULT);

        assertEquals(0, notifier.notifyTripsEndedYesterday(today));
        assertTrue(ownedNotifications(owner).isEmpty());
    }

    /**
     * 매일 도는 배치라 <b>코스당 한 번</b>이어야 한다.
     *
     * <p>안 그러면 답하지 않은 여행에 며칠 연속으로 알림이 쌓인다. 재실행 방어를 배치 기록이 아니라 유니크
     * 키에 얹었으므로, 같은 날 다시 돌려도 결과가 같아야 한다.
     */
    @Test
    void 두_번_돌아도_같은_코스에_알림은_하나다() {
        UUID owner = UUID.randomUUID();
        LocalDate today = LocalDate.of(2099, 10, 8);
        saveCourse(owner, today.minusDays(1), 1);

        assertEquals(1, notifier.notifyTripsEndedYesterday(today));
        assertEquals(0, notifier.notifyTripsEndedYesterday(today), "두 번째 실행은 새로 만들지 않아야 한다");
        assertEquals(1, ownedNotifications(owner).getTotalElements());
    }

    /**
     * 다음 날 돌아도 마찬가지다 — <b>이게 실제 운영 모양</b>이다.
     *
     * <p>어제 알림을 받은 코스는 오늘 기준으로는 "그저께 끝난 것" 이라 대상에서 빠진다. 대상 선정과 유니크 키가
     * 이중으로 막는 셈인데, 둘 중 하나만 믿으면 다른 하나가 무너질 때 며칠 연속 알림이 된다.
     */
    @Test
    void 다음_날_배치에서도_같은_코스에_다시_보내지_않는다() {
        UUID owner = UUID.randomUUID();
        LocalDate today = LocalDate.of(2099, 10, 15);
        saveCourse(owner, today.minusDays(1), 1);
        assertEquals(1, notifier.notifyTripsEndedYesterday(today));

        assertEquals(0, notifier.notifyTripsEndedYesterday(today.plusDays(1)));
        assertEquals(1, ownedNotifications(owner).getTotalElements());
    }

    /** 주인 없는 코스(공유 링크용, #261)는 알릴 상대가 없다. */
    @Test
    void 주인_없는_공유_전용_코스는_건너뛴다() {
        LocalDate today = LocalDate.of(2099, 10, 22);
        courseRepository.save(Course.sharedOnly(
                1L, Density.RELAXED, TransportMode.CAR, List.of(DaySchedule.of(1, List.of(slot()))),
                today.minusDays(1), 1, null, StartDayLeave.DEFAULT));

        assertEquals(0, notifier.notifyTripsEndedYesterday(today));
    }

    @Test
    void 대상이_없으면_아무것도_만들지_않는다() {
        assertEquals(0, notifier.notifyTripsEndedYesterday(LocalDate.of(2099, 11, 1)));
    }

    /**
     * <b>대상이 0건이어도 "돌았다" 는 남는다(#309).</b>
     *
     * <p>이 기록이 없어서 알림이 안 왔다는 제보에 답하지 못했다 — 배치가 안 돈 것인지, 돌았는데 대상이
     * 없었던 것인지 가를 근거가 로그뿐이었고 그 로그는 재배포로 사라진 뒤였다.
     *
     * <p><b>0건일 때를 고른 이유가 요지다.</b> 알림이 만들어진 날은 알림 자체가 증거지만, 0건인 날은 이
     * 기록 말고는 아무 흔적이 없다. 그날이 바로 답해야 하는 날이다.
     *
     * <p>스케줄러가 부르는 <b>인자 없는</b> 메서드를 부른다. 기록은 그 진입점에만 있어서, 날짜를 받는
     * 오버로드로는 이 경로가 검증되지 않는다.
     */
    @Test
    void 대상이_없어도_배치가_돌았다는_기록을_남긴다() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        notifier.notifyTripsEndedYesterday();

        assertTrue(batchRunRepository.hasRunOn("trip-after-notify", today),
                "배치가 돌았는데 실행 기록이 없다 — 안 돈 날과 구분되지 않는다");
    }

    /** 코스는 하루 이상이어야 성립하므로 최소 형태(하루 1슬롯)로 만든다. 이 테스트가 보는 것은 날짜뿐이다. */
    private Course saveCourse(UUID owner, LocalDate travelDate, int travelDays) {
        List<DaySchedule> days = IntStream.rangeClosed(1, travelDays)
                .mapToObj(day -> DaySchedule.of(day, List.of(slot(day))))
                .toList();
        return courseRepository.save(Course.ownedBy(
                owner, 1L, Density.RELAXED, TransportMode.CAR, days,
                travelDate, travelDays, null, StartDayLeave.DEFAULT));
    }

    private Slot slot() {
        return slot(1);
    }

    /**
     * 하루짜리 일정의 유일한 슬롯 — 순번은 <b>날짜가 아니라 그 날 안에서</b> 1 부터다.
     *
     * <p>날짜를 순번에 넣으면 둘째 날이 "1 위치에 2" 로 걸린다. 여기서 날짜는 장소 id 를 갈라 두는 데만 쓴다.
     */
    private Slot slot(int day) {
        return Slot.of(1, TimeOfDay.MORNING, SlotKind.SIGHT, "c" + day, "장소" + day, 37.50, 128.60, 0,
                new SlotDisplay(null, null, null, null));
    }

    private Page<Notification> ownedNotifications(UUID owner) {
        return notificationRepository.findByOwner(owner, PageRequest.of(0, 10));
    }
}
