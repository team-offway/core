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
import com.offway.core.liveactivity.domain.TripProgress;
import com.offway.core.liveactivity.infrastructure.apns.ApnsResult;
import com.offway.core.liveactivity.infrastructure.apns.LiveActivityPush;
import com.offway.core.liveactivity.infrastructure.apns.LiveActivitySender;
import com.offway.core.liveactivity.repository.LiveActivityTokenRepository;
import com.offway.core.liveactivity.service.dto.LiveActivityTarget;
import com.offway.core.transport.domain.TransportMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
 * 자정 갱신 배치가 <b>무엇을 보내고 무엇을 지우는가</b>(#575).
 *
 * <p>외부 경계인 APNs 만 프로그래머블 stub 으로 격리한다 — 토큰 저장소·코스 저장소는 내부라 실제 빈을 쓴다.
 *
 * <p>여기서 보는 것은 "DB 상태 + 결정" 이다. 오늘 날짜와 코스의 날짜를 맞대어 갱신인지 종료인지 가르고,
 * 보낸 뒤 어떤 행을 남기고 어떤 행을 지우는지가 DB 에 얹혀 있어 단위로는 확인할 수 없다.
 *
 * <p><b>건수로 단언하지 않는다.</b> 이 배치는 소유자를 가리지 않고 <b>등록된 카드를 전부</b> 훑는다.
 * 클래스 레벨 {@code @Transactional} 이 시나리오 사이를 갈라 주지만(테스트 규약), 건수 단언은 그
 * 격리에 기대는 순간 깨지기 쉬운 단언이 된다 — 시나리오마다 고유한 토큰을 쓰고 <b>그 토큰에 무엇이
 * 갔는지</b>로 단언한다. 그러면 무엇이 틀렸는지가 실패 메시지에 그대로 나온다.
 *
 * <p>발송은 가상 스레드로 팬아웃하지만 <b>DB 를 만지는 일은 전부 호출 스레드</b>에서 일어난다(대상
 * 조회·삭제). 그래서 테스트 트랜잭션 안에 그대로 들어온다.
 */
@SpringBootTest
@Transactional
@Import(LiveActivityRefreshIntegrationTest.StubSenderConfig.class)
class LiveActivityRefreshIntegrationTest {

    @Autowired
    private LiveActivityRefresher refresher;

    @Autowired
    private LiveActivityDispatcher dispatcher;

    @Autowired
    private LiveActivityTokenRepository liveActivityTokenRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private StubLiveActivitySender sender;

    @Test
    void 출발_전이면_남은_날을_보낸다() {
        sender.respondWith(token -> ApnsResult.SENT);
        UUID owner = UUID.randomUUID();
        LocalDate today = LocalDate.of(2098, 3, 1);
        String token = register(owner, today.plusDays(3), 3);

        refresher.refresh(today);

        LiveActivityPush.Update push = assertInstanceOf(LiveActivityPush.Update.class, sender.sentTo(token));
        assertEquals(3, push.daysLeft());
        assertNull(push.dayNth());
        assertEquals(today.plusDays(3), push.startDate());
        assertEquals(today.plusDays(5), push.endDate());
        assertTrue(rowExists(token), "아직 여행이 남았는데 등록이 사라졌다");
    }

    @Test
    void 여행_중이면_며칠째인지_보낸다() {
        sender.respondWith(token -> ApnsResult.SENT);
        UUID owner = UUID.randomUUID();
        LocalDate today = LocalDate.of(2098, 3, 10);
        String token = register(owner, today.minusDays(1), 3);

        refresher.refresh(today);

        LiveActivityPush.Update push = assertInstanceOf(LiveActivityPush.Update.class, sender.sentTo(token));
        assertEquals(2, push.dayNth());
        assertNull(push.daysLeft());
        assertTrue(rowExists(token));
    }

    /**
     * 여행이 끝나면 <b>종료를 보내고 등록을 지운다</b>.
     *
     * <p>안 보내면 끝난 여행이 잠금화면에 최대 12시간 남고, 안 지우면 내일도 같은 종료를 또 보낸다.
     */
    @Test
    void 끝난_여행은_종료를_보내고_등록을_지운다() {
        sender.respondWith(token -> ApnsResult.SENT);
        UUID owner = UUID.randomUUID();
        LocalDate today = LocalDate.of(2098, 3, 20);
        String token = register(owner, today.minusDays(5), 3);

        refresher.refresh(today);

        assertInstanceOf(LiveActivityPush.End.class, sender.sentTo(token));
        assertFalse(rowExists(token), "끝난 등록이 남았다 — 내일도 같은 종료를 또 보낸다");
    }

    /**
     * 코스가 사라진 등록도 <b>건너뛰지 않는다</b>.
     *
     * <p>사용자가 코스를 지웠거나 탈퇴 정리가 반쯤 돈 경우인데, 건너뛰면 없는 여행의 카드가 잠금화면에
     * 그대로 남는다.
     */
    @Test
    void 코스가_사라진_등록에는_종료를_보내고_지운다() {
        sender.respondWith(token -> ApnsResult.SENT);
        UUID owner = UUID.randomUUID();
        String token = uniqueToken("gone-course");
        liveActivityTokenRepository.register(
                LiveActivityToken.register(owner, 999_999_999L, token, LocalDateTime.now()));

        refresher.refresh(LocalDate.of(2098, 4, 1));

        assertInstanceOf(LiveActivityPush.End.class, sender.sentTo(token));
        assertFalse(rowExists(token));
    }

    /** 토큰이 죽었으면({@code 410}) 행을 지운다 — 계속 두면 매일 실패한다. */
    @Test
    void 죽은_토큰은_지운다() {
        UUID owner = UUID.randomUUID();
        LocalDate today = LocalDate.of(2098, 5, 1);
        String token = register(owner, today.plusDays(2), 2);
        sender.respondWith(sent -> token.equals(sent) ? ApnsResult.GONE : ApnsResult.SENT);

        refresher.refresh(today);

        assertFalse(rowExists(token), "죽은 토큰이 남았다 — 매일 실패하는 발송이 쌓인다");
    }

    /**
     * 일시 실패한 토큰은 <b>지우지 않는다</b>.
     *
     * <p>네트워크 한 번 흔들렸다고 등록을 지우면, 다음에 성공했을 토큰을 잃는다. 사용자는 앱을 다시 열기
     * 전까지 잠금화면이 영영 안 바뀐다.
     */
    @Test
    void 일시_실패한_토큰은_남긴다() {
        UUID owner = UUID.randomUUID();
        LocalDate today = LocalDate.of(2098, 5, 10);
        String token = register(owner, today.plusDays(2), 2);
        sender.respondWith(sent -> token.equals(sent) ? ApnsResult.FAILED : ApnsResult.SENT);

        refresher.refresh(today);

        assertTrue(rowExists(token), "일시 실패로 등록을 지웠다 — 다음에 성공할 토큰을 잃는다");
    }

    /**
     * 같은 코스를 두 번 등록해도 카드는 하나다.
     *
     * <p>앱은 네트워크가 끊기면 성공 여부를 모른 채 재시도한다. 행이 늘면 같은 잠금화면에 갱신이 두 번 간다.
     */
    @Test
    void 같은_코스를_다시_등록하면_토큰만_갈린다() {
        sender.respondWith(token -> ApnsResult.SENT);
        UUID owner = UUID.randomUUID();
        LocalDate today = LocalDate.of(2098, 6, 1);
        Course course = saveCourse(owner, today.plusDays(2), 2);
        String first = uniqueToken("first");
        String second = uniqueToken("second");
        liveActivityTokenRepository.register(
                LiveActivityToken.register(owner, course.getId(), first, LocalDateTime.now()));
        liveActivityTokenRepository.register(
                LiveActivityToken.register(owner, course.getId(), second, LocalDateTime.now()));

        refresher.refresh(today);

        assertNull(sender.sentTo(first), "옛 토큰에도 보냈다 — 같은 카드에 갱신이 두 번 간다");
        assertNotNull(sender.sentTo(second));
        assertFalse(rowExists(first));
        assertTrue(rowExists(second));
    }

    /**
     * 속도 제한을 만나면 <b>남은 것은 비켜선다</b>(#577 리뷰).
     *
     * <p>처음에는 발송을 시작하기 전에만 플래그를 봤는데, 가상 스레드 풀은 제출된 일을 곧바로 각자의
     * 스레드에서 시작하므로 거의 전부가 그 검사를 이미 지나 세마포어에서 기다리고 있었다. 그래서 어느
     * 한 건이 {@code 429} 를 받아도 대기자들은 순서대로 permit 을 받아 <b>전부 계속 쏘았다</b> —
     * 물러나려던 것이 앞 16건 이후로는 아무 일도 하지 않았다.
     *
     * <p>이미 들어와 있던 만큼(동시 상한)은 마저 나가는 것이 정상이다. 그 위로 넘어가면 비켜서는
     * 장치가 없는 것이다.
     */
    @Test
    void 속도_제한을_만나면_남은_카드는_비켜선다() {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch inFlight = new CountDownLatch(LiveActivityDispatcher.MAX_CONCURRENT_SENDS);
        CountDownLatch release = new CountDownLatch(1);
        sender.respondWith(token -> {
            calls.incrementAndGet();
            inFlight.countDown();
            // 상한만큼이 모두 안에 들어올 때까지 붙잡아 둔다 — 그래야 나머지가 "기다리는 중" 이 되고,
            // 이 테스트가 보려는 상황(대기자들이 플래그를 다시 보는가)이 실제로 만들어진다.
            await(release);
            return ApnsResult.THROTTLED;
        });
        int cards = LiveActivityDispatcher.MAX_CONCURRENT_SENDS * 4;
        List<LiveActivityTarget> targets = new ArrayList<>();
        for (int i = 0; i < cards; i++) {
            targets.add(LiveActivityTarget.builder()
                    .rowId(-(i + 1L)) // 없는 행 — 이 시나리오는 삭제를 보지 않는다
                    .token("throttle-" + i)
                    .courseId(1L)
                    .progress(TripProgress.of(LocalDate.of(2098, 7, 1), 2, LocalDate.of(2098, 6, 29)))
                    .regionName("정선군")
                    .startDate(LocalDate.of(2098, 7, 1))
                    .endDate(LocalDate.of(2098, 7, 2))
                    .build());
        }

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> dispatching = pool.submit(() -> dispatcher.dispatch(targets));
            assertTrue(awaitLatch(inFlight), "동시 상한만큼이 발송에 들어오지 못했다");
            release.countDown();

            assertEquals(0, get(dispatching), "속도 제한을 받았는데 보낸 것으로 셌다");
        } finally {
            pool.shutdownNow();
        }

        assertTrue(calls.get() <= LiveActivityDispatcher.MAX_CONCURRENT_SENDS,
                "제한을 받은 뒤에도 계속 쏘았다 — 보낸 호출=" + calls.get() + "건, 카드=" + cards + "건");
    }

    private static boolean awaitLatch(CountDownLatch latch) {
        try {
            return latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static int get(Future<Integer> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String register(UUID owner, LocalDate travelDate, int travelDays) {
        Course course = saveCourse(owner, travelDate, travelDays);
        String token = uniqueToken("card");
        liveActivityTokenRepository.register(
                LiveActivityToken.register(owner, course.getId(), token, LocalDateTime.now()));
        return token;
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

    /** 코스는 기간만큼의 일정이 있어야 성립한다. 이 테스트가 보는 것은 날짜뿐이라 최소 형태로 만든다. */
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

    private boolean rowExists(String token) {
        return liveActivityTokenRepository.findAll().stream()
                .anyMatch(row -> token.equals(row.getToken()));
    }

    private static String uniqueToken(String label) {
        return "la-" + label + "-" + UUID.randomUUID();
    }

    /**
     * APNs stub — <b>무엇을</b> 보냈는지까지 붙잡는다.
     *
     * <p>결과값만 보면 "갱신 대신 종료를 보냈다" 같은 사고를 못 잡는다. 실어 보낸 내용이 곧 사용자가
     * 잠금화면에서 보는 것이라, 이 테스트가 지키려는 것이 그쪽이다.
     */
    static class StubLiveActivitySender implements LiveActivitySender {

        /** default 는 throw 다 — 명시 세팅을 빠뜨리면 즉시 깨져, 앞 테스트의 상태가 살아남는 함정을 막는다. */
        private volatile Function<String, ApnsResult> behavior = token -> {
            throw new IllegalStateException("이 테스트는 발송 결과를 정하지 않았습니다");
        };

        private final Map<String, LiveActivityPush> sent = new ConcurrentHashMap<>();

        void respondWith(Function<String, ApnsResult> behavior) {
            this.behavior = behavior;
            sent.clear();
        }

        LiveActivityPush sentTo(String token) {
            return sent.get(token);
        }

        @Override
        public ApnsResult send(String token, LiveActivityPush push) {
            sent.put(token, push);
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
