package com.offway.core.liveactivity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.offway.core.liveactivity.domain.LiveActivityToken;
import com.offway.core.liveactivity.domain.PushToStartToken;
import com.offway.core.liveactivity.infrastructure.apns.ApnsResult;
import com.offway.core.liveactivity.infrastructure.apns.LiveActivityPush;
import com.offway.core.liveactivity.infrastructure.apns.LiveActivitySender;
import com.offway.core.liveactivity.repository.LiveActivityTokenRepository;
import com.offway.core.liveactivity.repository.PushToStartTokenRepository;
import com.offway.core.transport.domain.TransportMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정오 배치가 <b>카드를 띄우는가, 갱신하는가</b>(#583).
 *
 * <p>외부 경계인 APNs 만 프로그래머블 stub 으로 격리한다 — 저장소·코스는 내부라 실제 빈을 쓴다.
 *
 * <p>여기서 보는 것은 "DB 상태 + 결정" 이다. 그 (사용자, 코스)의 갱신 토큰이 있느냐로 {@code start} 와
 * {@code update} 가 갈리고, 그 판정이 표 두 개에 얹혀 있어 단위로는 확인할 수 없다.
 *
 * <p><b>시나리오마다 고유한 토큰을 쓰고 그 토큰에 무엇이 갔는지로 단언한다.</b> 이 배치는 소유자를
 * 가리지 않고 등록된 기기를 전부 훑으므로, 건수로 단언하면 격리에 기대는 깨지기 쉬운 단언이 된다.
 */
@SpringBootTest
@Transactional
@Import(LiveActivityStartIntegrationTest.StubSenderConfig.class)
class LiveActivityStartIntegrationTest {

    /** 다른 테스트가 쓰지 않을 먼 미래 — 이 배치는 소유자를 안 가리므로 날짜도 갈라 둔다. */
    private static final LocalDate TODAY = LocalDate.of(2097, 4, 1);

    @Autowired
    private LiveActivityStarter starter;

    @Autowired
    private PushToStartTokenRepository pushToStartTokenRepository;

    @Autowired
    private LiveActivityTokenRepository liveActivityTokenRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private StubLiveActivitySender sender;

    @Test
    void 카드가_없으면_띄운다() {
        sender.respondWith(token -> ApnsResult.SENT);
        UUID owner = UUID.randomUUID();
        Course course = saveCourse(owner, TODAY.plusDays(3), 3);
        String device = registerDevice(owner);

        starter.start(TODAY);

        LiveActivityPush.Start push = assertInstanceOf(LiveActivityPush.Start.class, sender.sentTo(device));
        assertEquals(course.getId(), push.courseId());
        assertEquals(3, push.state().daysLeft());
        assertEquals(TODAY.plusDays(3), push.state().startDate());
        assertEquals(TODAY.plusDays(5), push.state().endDate());
    }

    /**
     * <b>이미 떠 있으면 띄우지 않는다.</b>
     *
     * <p>같은 카드에 {@code start} 를 또 보내면 하나가 더 생겨 잠금화면에 둘이 뜬다.
     */
    @Test
    void 이미_떠_있으면_갱신만_한다() {
        sender.respondWith(token -> ApnsResult.SENT);
        UUID owner = UUID.randomUUID();
        Course course = saveCourse(owner, TODAY.plusDays(2), 2);
        String device = registerDevice(owner);
        String card = registerCard(owner, course.getId());

        starter.start(TODAY);

        assertNull(sender.sentTo(device), "이미 떠 있는데 또 띄웠다 — 잠금화면에 카드가 둘이 된다");
        assertInstanceOf(LiveActivityPush.Update.class, sender.sentTo(card));
    }

    /**
     * <b>갱신 토큰이 남아 있어도 카드는 죽어 있을 수 있다</b>(8시간이 지났다).
     *
     * <p>그때 APNs 가 {@code 410} 을 주는데, 그것을 다음 회차로 미루면 <b>그날 카드를 통째로 놓친다</b> —
     * 이 기능이 고치려던 바로 그 증상이다. 같은 회차에서 행을 지우고 띄우기로 넘어간다.
     */
    @Test
    void 갱신이_410이면_같은_회차에서_띄운다() {
        UUID owner = UUID.randomUUID();
        Course course = saveCourse(owner, TODAY.plusDays(1), 2);
        String device = registerDevice(owner);
        String card = registerCard(owner, course.getId());
        sender.respondWith(token -> card.equals(token) ? ApnsResult.GONE : ApnsResult.SENT);

        starter.start(TODAY);

        assertInstanceOf(LiveActivityPush.Update.class, sender.sentTo(card));
        assertInstanceOf(LiveActivityPush.Start.class, sender.sentTo(device));
        assertFalse(cardExists(card), "죽은 갱신 토큰이 남았다 — 내일도 같은 410 을 받는다");
    }

    /** 띄우기 토큰이 죽었으면({@code 410}) 그 행을 지운다 — 앱을 지웠거나 재설치했다. */
    @Test
    void 죽은_띄우기_토큰은_지운다() {
        UUID owner = UUID.randomUUID();
        saveCourse(owner, TODAY.plusDays(2), 2);
        String device = registerDevice(owner);
        sender.respondWith(token -> device.equals(token) ? ApnsResult.GONE : ApnsResult.SENT);

        starter.start(TODAY);

        assertFalse(deviceExists(device), "죽은 토큰이 남았다 — 매일 실패하는 발송이 쌓인다");
    }

    /**
     * 일시 실패한 토큰은 <b>지우지 않는다</b>.
     *
     * <p>네트워크가 한 번 흔들렸다고 등록을 지우면, 그 사람은 앱을 다시 열기 전까지 카드를 영영 못 받는다.
     */
    @Test
    void 일시_실패한_띄우기_토큰은_남긴다() {
        UUID owner = UUID.randomUUID();
        saveCourse(owner, TODAY.plusDays(2), 2);
        String device = registerDevice(owner);
        sender.respondWith(token -> device.equals(token) ? ApnsResult.FAILED : ApnsResult.SENT);

        starter.start(TODAY);

        assertTrue(deviceExists(device), "일시 실패로 등록을 지웠다 — 다음에 성공할 토큰을 잃는다");
    }

    /** 여행이 창 밖이면 아무것도 안 보낸다 — 6일 뒤 출발은 아직 아니다. */
    @Test
    void 창_밖이면_안_띄운다() {
        sender.respondWith(token -> ApnsResult.SENT);
        UUID owner = UUID.randomUUID();
        saveCourse(owner, TODAY.plusDays(6), 2);
        String device = registerDevice(owner);

        starter.start(TODAY);

        assertNull(sender.sentTo(device));
    }

    @Test
    void 여행이_없으면_안_띄운다() {
        sender.respondWith(token -> ApnsResult.SENT);
        String device = registerDevice(UUID.randomUUID());

        starter.start(TODAY);

        assertNull(sender.sentTo(device));
    }

    /**
     * <b>사람당 카드 하나.</b>
     *
     * <p>잠금화면은 자리가 하나다. 둘을 띄우면 서로를 밀어내고, 사용자는 어느 것이 "다음 여행" 인지
     * 알 수 없다. 진행 중이 앞으로 떠날 것을 이긴다.
     */
    @Test
    void 여행이_둘이면_진행_중인_것만_띄운다() {
        sender.respondWith(token -> ApnsResult.SENT);
        UUID owner = UUID.randomUUID();
        Course ongoing = saveCourse(owner, TODAY.minusDays(1), 3);
        saveCourse(owner, TODAY.plusDays(1), 2);
        String device = registerDevice(owner);

        starter.start(TODAY);

        LiveActivityPush.Start push = assertInstanceOf(LiveActivityPush.Start.class, sender.sentTo(device));
        assertEquals(ongoing.getId(), push.courseId());
        assertEquals(2, push.state().dayNth(), "여행 중이면 며칠째를 보낸다");
        assertNull(push.state().daysLeft());
    }

    /** 기기가 둘이면 둘 다에 띄운다 — 폰과 태블릿 양쪽 잠금화면에 카드가 있어야 한다. */
    @Test
    void 기기가_둘이면_둘_다에_띄운다() {
        sender.respondWith(token -> ApnsResult.SENT);
        UUID owner = UUID.randomUUID();
        Course course = saveCourse(owner, TODAY.plusDays(2), 2);
        String phone = registerDevice(owner);
        String tablet = registerDevice(owner);

        starter.start(TODAY);

        assertNotNull(sender.sentTo(phone));
        assertNotNull(sender.sentTo(tablet));
        LiveActivityPush.Start push = assertInstanceOf(LiveActivityPush.Start.class, sender.sentTo(tablet));
        assertEquals(course.getId(), push.courseId(), "두 기기가 같은 여행을 띄워야 한다");
    }

    /** 같은 토큰을 다시 등록해도 행이 늘지 않는다 — 앱은 시작할 때마다 보낸다. */
    @Test
    void 같은_토큰을_다시_등록해도_한_번만_띄운다() {
        sender.respondWith(token -> ApnsResult.SENT);
        UUID owner = UUID.randomUUID();
        saveCourse(owner, TODAY.plusDays(2), 2);
        String device = "pts-" + UUID.randomUUID();
        register(owner, device);
        register(owner, device);

        starter.start(TODAY);

        assertEquals(1, sender.countTo(device), "같은 기기에 두 번 보냈다 — 카드가 둘이 된다");
    }

    private String registerDevice(UUID owner) {
        String token = "pts-" + UUID.randomUUID();
        register(owner, token);
        return token;
    }

    private void register(UUID owner, String token) {
        pushToStartTokenRepository.register(PushToStartToken.register(owner, token, LocalDateTime.now()));
    }

    private String registerCard(UUID owner, Long courseId) {
        String token = "card-" + UUID.randomUUID();
        liveActivityTokenRepository.register(
                LiveActivityToken.register(owner, courseId, token, LocalDateTime.now()));
        return token;
    }

    private boolean deviceExists(String token) {
        return pushToStartTokenRepository.findAll().stream()
                .anyMatch(row -> token.equals(row.getToken()));
    }

    private boolean cardExists(String token) {
        return liveActivityTokenRepository.findAll().stream()
                .anyMatch(row -> token.equals(row.getToken()));
    }

    private Course saveCourse(UUID owner, LocalDate travelDate, int travelDays) {
        return courseRepository.save(Course.ownedBy(
                owner,
                1L,
                Density.RELAXED,
                TransportMode.CAR,
                schedules(travelDays),
                travelDate,
                travelDays,
                null,
                StartDayLeave.DEFAULT,
                null));
    }

    private static List<DaySchedule> schedules(int travelDays) {
        List<DaySchedule> days = new ArrayList<>();
        for (int dayNumber = 1; dayNumber <= travelDays; dayNumber++) {
            days.add(DaySchedule.of(dayNumber, List.of(minimalSlot())));
        }
        return days;
    }

    private static Slot minimalSlot() {
        return Slot.of(1, TimeOfDay.MORNING, SlotKind.SIGHT, "c1", "장소1", 37.50, 128.60, 0,
                new SlotDisplay(null, null, null, null));
    }

    /** APNs stub — <b>무엇을</b> 보냈는지와 <b>몇 번</b> 보냈는지까지 붙잡는다. */
    static class StubLiveActivitySender implements LiveActivitySender {

        /** default 는 throw 다 — 명시 세팅을 빠뜨리면 즉시 깨져, 앞 테스트의 상태가 살아남는 함정을 막는다. */
        private volatile Function<String, ApnsResult> behavior = token -> {
            throw new IllegalStateException("이 테스트는 발송 결과를 정하지 않았습니다");
        };

        private final Map<String, LiveActivityPush> sent = new ConcurrentHashMap<>();
        private final Map<String, Integer> counts = new ConcurrentHashMap<>();

        void respondWith(Function<String, ApnsResult> behavior) {
            this.behavior = behavior;
            sent.clear();
            counts.clear();
        }

        LiveActivityPush sentTo(String token) {
            return sent.get(token);
        }

        int countTo(String token) {
            return counts.getOrDefault(token, 0);
        }

        @Override
        public ApnsResult send(String token, LiveActivityPush push) {
            // 띄우기가 갱신을 덮어쓰지 않게 — 한 토큰에 둘이 갈 일은 없지만, 덮이면 410 분기를 못 본다.
            sent.putIfAbsent(token, push);
            counts.merge(token, 1, Integer::sum);
            return behavior.apply(token);
        }
    }

    @TestConfiguration
    static class StubSenderConfig {

        @Bean
        @Primary
        StubLiveActivitySender stubLiveActivitySender() {
            return new StubLiveActivitySender();
        }
    }
}
