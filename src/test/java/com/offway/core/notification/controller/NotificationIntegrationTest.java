package com.offway.core.notification.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.offway.core.notification.domain.Notification;
import com.offway.core.notification.domain.NotificationType;
import com.offway.core.notification.repository.NotificationRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림 조회·읽음의 HTTP 계약(#263).
 *
 * <p>알림을 만드는 API 는 이 범위에 없다(생성은 후속). 그래서 준비는 리포지토리로 직접 넣는다 — 내부
 * 컴포넌트라 stub 이 아니라 실제 빈이다.
 *
 * <p><b>소유자를 테스트마다 다르게 쓴다.</b> 이 클래스는 DB 를 롤백하지 않아(컨텍스트를 공유하는 다른
 * 컨트롤러 통합 테스트와 같다) 같은 키를 쓰면 앞 테스트의 잔여 상태가 다음 시나리오로 새어 든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class NotificationIntegrationTest {

    private static final String URL = "/api/v1/notifications";
    private static final String READ_URL = URL + "/{notificationId}/read";
    private static final String READ_ALL_URL = URL + "/read-all";
    private static final String GUEST_HEADER = "X-Guest-Id";

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 8, 13, 9, 0);

    /**
     * 동시성 테스트가 기다릴 상한.
     *
     * <p>상한이 없으면 워커가 교착되거나 응답하지 않을 때 {@code barrier.await()}·{@code Future.get()} 이
     * 무기한 매달린다 — {@code finally} 의 {@code shutdownNow()} 에도 닿지 못해 <b>실패가 보고되지 않고
     * 빌드가 멎는다.</b> 매달림과 실패는 다른 신호여야 한다.
     */
    private static final long CONCURRENCY_TIMEOUT_SECONDS = 30;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Notification given(String guestId, Long courseId, int minutesAfterBase) {
        return notificationRepository.save(Notification.builder()
                .guestId(guestId)
                .type(NotificationType.TRIP_TOMORROW)
                .courseId(courseId)
                .createdAt(BASE_TIME.plusMinutes(minutesAfterBase))
                .build());
    }

    @Test
    void 알림이_없으면_빈_목록과_안읽음_0을_준다() throws Exception {
        // 없는 소유자를 404 로 돌려주면 클라이언트가 "처음 쓰는 사람" 을 예외로 다뤄야 한다.
        mockMvc.perform(get(URL).header(GUEST_HEADER, "noti-empty"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.detail").value("요청이 정상 처리되었습니다."))
                .andExpect(jsonPath("$.data.notifications.length()").value(0))
                .andExpect(jsonPath("$.data.unreadCount").value(0))
                .andExpect(jsonPath("$.pageResponse.totalElements").value(0));
    }

    @Test
    void 목록은_최신순이고_type과_courseId를_싣는다() throws Exception {
        String guest = "noti-order";
        given(guest, 11L, 0);
        given(guest, null, 10);
        Notification newest = given(guest, 12L, 20);

        mockMvc.perform(get(URL).header(GUEST_HEADER, guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.notifications.length()").value(3))
                .andExpect(jsonPath("$.data.notifications[0].id").value(newest.getId()))
                .andExpect(jsonPath("$.data.notifications[0].type").value("TRIP_TOMORROW"))
                .andExpect(jsonPath("$.data.notifications[0].courseId").value(12))
                .andExpect(jsonPath("$.data.notifications[0].read").value(false))
                // 코스와 무관한 알림은 courseId 가 비어 나간다 — 앱이 이동을 걸지 않는 신호다.
                .andExpect(jsonPath("$.data.notifications[1].courseId").doesNotExist())
                .andExpect(jsonPath("$.data.unreadCount").value(3));
    }

    @Test
    void 안읽음_수는_페이지가_아니라_전체를_센다() throws Exception {
        // 배지가 쓰는 값이라 페이지 안에서 세면 첫 페이지 크기에서 멈춘다.
        String guest = "noti-paged";
        given(guest, 1L, 0);
        given(guest, 2L, 10);
        given(guest, 3L, 20);

        mockMvc.perform(get(URL).header(GUEST_HEADER, guest).param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications.length()").value(1))
                .andExpect(jsonPath("$.data.unreadCount").value(3))
                .andExpect(jsonPath("$.pageResponse.page").value(0))
                .andExpect(jsonPath("$.pageResponse.size").value(1))
                .andExpect(jsonPath("$.pageResponse.totalElements").value(3))
                .andExpect(jsonPath("$.pageResponse.totalPages").value(3));
    }

    @Test
    void 같은_시각_알림도_페이지_경계에서_겹치거나_빠지지_않는다() throws Exception {
        // 여행 전날 배치는 여러 건을 같은 시각으로 넣는다. createdAt 이 같은 행들이 페이지 경계에서
        // 겹치거나 빠지지 않는지 — 페이지를 이어 붙인 결과가 넣은 것과 정확히 같은지로 확인한다.
        //
        // **이 테스트가 id 2차 정렬을 잠그지는 못한다**(실측). `OrderByCreatedAtDescIdDesc` 에서 `IdDesc`
        // 를 떼도 그대로 통과한다 — 인덱스가 (guest_id, created_at) 이고 InnoDB 는 그 뒤에 PK 를 붙이므로,
        // 지금 플랜(인덱스 역순 스캔)에서는 id 내림차순이 우연히 따라온다. tie-break 를 지키는 것은 이
        // 테스트가 아니라 쿼리의 ORDER BY 자체이고, 인덱스나 플랜이 바뀌면 조용히 깨질 수 있는 자리다.
        // 그래도 이 테스트는 남긴다 — 페이지네이션 계약(최신순·중복·누락)은 여기서만 회귀를 잡는다.
        String guest = "noti-tiebreak";
        List<Long> saved = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            saved.add(given(guest, (long) i, 0).getId());
        }

        List<Long> pagedThrough = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            String body = mockMvc.perform(get(URL)
                            .header(GUEST_HEADER, guest)
                            .param("page", String.valueOf(page))
                            .param("size", "2"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            List<Number> ids = JsonPath.read(body, "$.data.notifications[*].id");
            ids.forEach(id -> pagedThrough.add(id.longValue()));
        }

        // 세 페이지를 이어 붙이면 넣은 5건이 최신순(= id 내림차순)으로 정확히 한 번씩 나온다.
        List<Long> newestFirst = new ArrayList<>(saved);
        Collections.reverse(newestFirst);
        assertEquals(newestFirst, pagedThrough);
    }

    @Test
    void 하나_읽으면_안읽음이_줄고_다시_읽어도_성공한다() throws Exception {
        String guest = "noti-read-one";
        Notification target = given(guest, 5L, 0);
        given(guest, 6L, 10);

        mockMvc.perform(patch(READ_URL, target.getId()).header(GUEST_HEADER, guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        // 두 번째 요청도 성공이다 — 사용자가 원한 상태가 이미 이뤄져 있고, 개수도 더 줄지 않는다.
        mockMvc.perform(patch(READ_URL, target.getId()).header(GUEST_HEADER, guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        mockMvc.perform(get(URL).header(GUEST_HEADER, guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications[1].read").value(true))
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test
    void 남의_알림은_없는_알림과_똑같이_404다() throws Exception {
        // 403 으로 나누면 "그 id 는 존재한다" 를 알려주는 셈이라 id 를 훑어 남의 알림을 확인할 수 있다.
        Notification othersNotification = given("noti-owner", 7L, 0);

        mockMvc.perform(patch(READ_URL, othersNotification.getId()).header(GUEST_HEADER, "noti-stranger"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOTIFICATION-002"))
                .andExpect(jsonPath("$.detail").value("요청한 알림을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(patch(READ_URL, 999_999_999L).header(GUEST_HEADER, "noti-stranger"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION-002"));

        // 남의 알림은 그대로 안 읽음이어야 한다 — 404 를 준 뒤 조용히 고쳐놓으면 최악이다.
        mockMvc.perform(get(URL).header(GUEST_HEADER, "noti-owner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test
    void 전체_읽음은_읽을_것이_없어도_성공한다() throws Exception {
        String guest = "noti-read-all";
        given(guest, 1L, 0);
        given(guest, 2L, 10);
        given(guest, 3L, 20);

        mockMvc.perform(post(READ_ALL_URL).header(GUEST_HEADER, guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        mockMvc.perform(post(READ_ALL_URL).header(GUEST_HEADER, guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        mockMvc.perform(get(URL).header(GUEST_HEADER, guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications[0].read").value(true))
                .andExpect(jsonPath("$.data.notifications[2].read").value(true))
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    void 전체_읽음은_남의_알림을_건드리지_않는다() throws Exception {
        given("noti-mine", 1L, 0);
        given("noti-yours", 2L, 0);

        mockMvc.perform(post(READ_ALL_URL).header(GUEST_HEADER, "noti-mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        mockMvc.perform(get(URL).header(GUEST_HEADER, "noti-yours"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test
    void 게스트_헤더가_비면_400이다() throws Exception {
        // 빈 헤더는 @RequestHeader 를 통과한다 — 도메인까지 흘려보내면 500 이 나간다.
        mockMvc.perform(get(URL).header(GUEST_HEADER, " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("NOTIFICATION-001"))
                .andExpect(jsonPath("$.detail").value("게스트 식별자가 올바르지 않습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(post(READ_ALL_URL).header(GUEST_HEADER, " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOTIFICATION-001"));
    }

    @Test
    void 게스트_헤더가_아예_없으면_400이다() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    /**
     * 같은 알림을 동시에 읽어도 <b>둘 다 200 이다</b> — HTTP 계약 쪽.
     *
     * <p>목록에서 눌러 들어가며 같은 요청이 두 번 나가기 쉬운 자리다. 진 쪽이 실패하면 사용자는 아무 잘못도
     * 하지 않았는데 오류를 본다.
     *
     * <p><b>"한 번만 기록됐는가" 는 여기서 확인하지 않는다.</b> 그 계약은 응답에 드러나지 않으므로
     * {@link #동시에_읽으면_한_번만_기록된다()} 가 맡는다.
     */
    @Test
    void 동시에_읽어도_둘_다_성공한다() throws Exception {
        String guest = "noti-race-" + UUID.randomUUID();
        Notification target = given(guest, null, 0);
        int attempts = 2;
        CyclicBarrier barrier = new CyclicBarrier(attempts);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                results.add(executor.submit(() -> {
                    barrier.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    // 클래스의 @WithMockUser 는 이 스레드에 안 따라온다 — 요청마다 인증을 싣는다.
                    return mockMvc.perform(patch(READ_URL, target.getId())
                                    .with(user("dev"))
                                    .header(GUEST_HEADER, guest))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                }));
            }
            for (Future<Integer> result : results) {
                assertEquals(
                        200, result.get(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS), "동시 읽음 중 하나가 실패했다");
            }
        } finally {
            executor.shutdownNow();
        }

        Long read = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE id = ? AND read_at IS NOT NULL", Long.class, target.getId());
        assertEquals(1L, read, "읽음으로 바뀌지 않았다");
    }

    /**
     * 동시에 읽으면 <b>실제로 기록한 호출은 하나뿐이다</b>.
     *
     * <p>{@code read_at} 이 채워졌는지 세는 것으로는 이 계약이 안 잡힌다 — 조건 없는 UPDATE 가 두 번 돌아
     * 나중 쪽이 처음 읽은 시각을 덮어써도 "채워진 행 하나" 는 그대로다. 시각을 직접 비교하는 것도 못 쓴다:
     * 두 호출이 같은 밀리초를 쓰면 덮어썼는지 아닌지 구별되지 않는다.
     *
     * <p><b>바꾼 행 수를 본다.</b> 조건부 UPDATE 면 이긴 쪽만 1 이고 진 쪽은 0 이다 — 판정이 DB 안에서
     * 갈렸다는 직접 증거다. 이 값은 응답에 실리지 않으므로(둘 다 200 이 계약이다) port 를 직접 부른다.
     * 내부 컴포넌트라 stub 이 아니라 실제 빈이고, {@code @Modifying} 이 트랜잭션을 요구하므로 호출마다
     * 하나를 열어 준다.
     */
    @Test
    void 동시에_읽으면_한_번만_기록된다() throws Exception {
        String guest = "noti-claim-" + UUID.randomUUID();
        Notification target = given(guest, null, 0);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        int attempts = 2;
        CyclicBarrier barrier = new CyclicBarrier(attempts);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        List<Integer> changed = new ArrayList<>();
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                results.add(executor.submit(() -> {
                    barrier.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    return transaction.execute(status ->
                            notificationRepository.markRead(guest, target.getId(), BASE_TIME.plusHours(1)));
                }));
            }
            for (Future<Integer> result : results) {
                changed.add(result.get(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, Collections.frequency(changed, 1), "기록에 성공한 호출이 하나여야 한다 실제=" + changed);
        assertEquals(1, Collections.frequency(changed, 0), "이미 읽음으로 갈린 호출이 하나여야 한다 실제=" + changed);
    }
}
