package com.offway.core.notification.controller;

import static com.offway.core.user.config.TestLogins.loginAs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.offway.core.notification.domain.Notification;
import com.offway.core.notification.domain.NotificationType;
import com.offway.core.notification.repository.NotificationRepository;
import com.offway.core.user.config.WithLoginUser;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림 조회·읽음의 HTTP 계약(#263).
 *
 * <p>알림을 만드는 API 는 이 범위에 없다(생성은 배치다). 그래서 준비는 리포지토리로 직접 넣는다 — 내부
 * 컴포넌트라 stub 이 아니라 실제 빈이다.
 *
 * <p><b>소유자를 요청이 정하지 않는다</b>(#280). 예전에는 {@code X-Guest-Id} 헤더를 실어 보냈고, 그래서
 * 헤더 값만 바꾸면 남의 알림을 읽을 수 있었다. 지금 주인은 인증이 정하므로 테스트도 <b>헤더가 아니라
 * 로그인 사용자</b>를 바꿔 시나리오를 만든다({@link WithLoginUser}).
 *
 * <p><b>소유자를 테스트마다 다르게 쓴다.</b> 이 클래스는 DB 를 롤백하지 않아(컨텍스트를 공유하는 다른
 * 컨트롤러 통합 테스트와 같다) 같은 UUID 를 쓰면 앞 테스트의 잔여 상태가 다음 시나리오로 새어 든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NotificationIntegrationTest {

    private static final String URL = "/api/v1/notifications";
    private static final String READ_URL = URL + "/{notificationId}/read";
    private static final String READ_ALL_URL = URL + "/read-all";

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 8, 13, 9, 0);

    /**
     * 동시성 테스트가 기다릴 상한.
     *
     * <p>상한이 없으면 워커가 교착되거나 응답하지 않을 때 {@code barrier.await()}·{@code Future.get()} 이
     * 무기한 매달린다 — {@code finally} 의 {@code shutdownNow()} 에도 닿지 못해 <b>실패가 보고되지 않고
     * 빌드가 멎는다.</b> 매달림과 실패는 다른 신호여야 한다.
     */
    private static final long CONCURRENCY_TIMEOUT_SECONDS = 30;

    /** {@code SecurityConfig} 가 요구하는 권한 — {@code WithLoginUserSecurityContextFactory} 와 같은 값이다. */
    private static final String APP_USER_AUTHORITY = "ROLE_USER";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 이 실행에만 속하는 주인.
     *
     * <p>예전에는 시나리오마다 고정 UUID 상수를 뒀다. {@code @WithLoginUser} 에 값을 적으려면 컴파일
     * 상수여야 하기 때문인데, 이 클래스는 <b>DB 를 롤백하지 않아</b> 같은 DB 로 두 번째로 돌리면 앞
     * 실행의 알림이 남아 건수 단언이 깨진다. 어노테이션을 떼고 본문에서 만든 주인을 요청마다 실어
     * 보내면 그 결합이 사라진다.
     */
    private static String newOwner() {
        return UUID.randomUUID().toString();
    }

    /** 그 주인으로 로그인한 요청을 만든다 — {@code @WithLoginUser} 를 대신한다. */
    private static RequestPostProcessor as(String owner) {
        return loginAs(UUID.fromString(owner));
    }

    private Notification given(String owner, Long courseId, int minutesAfterBase) {
        return notificationRepository.save(Notification.builder()
                .userId(UUID.fromString(owner))
                .type(NotificationType.TRIP_TOMORROW)
                .courseId(courseId)
                .createdAt(BASE_TIME.plusMinutes(minutesAfterBase))
                .build());
    }


    @Test
    @WithLoginUser
    void 알림이_없으면_빈_목록과_안읽음_0을_준다() throws Exception {
        // 처음 로그인한 사람을 404 로 돌려주면 클라이언트가 "알림이 아직 없는 상태" 를 예외로 다뤄야 한다.
        mockMvc.perform(get(URL))
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
        String owner = newOwner();
        given(owner, 11L, 0);
        given(owner, null, 10);
        Notification newest = given(owner, 12L, 20);

        mockMvc.perform(get(URL).with(as(owner)))
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
        String owner = newOwner();
        // 배지가 쓰는 값이라 페이지 안에서 세면 첫 페이지 크기에서 멈춘다.
        given(owner, 1L, 0);
        given(owner, 2L, 10);
        given(owner, 3L, 20);

        mockMvc.perform(get(URL).with(as(owner)).param("page", "0").param("size", "1"))
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
        String owner = newOwner();
        // 여행 전날 배치는 여러 건을 같은 시각으로 넣는다. createdAt 이 같은 행들이 페이지 경계에서
        // 겹치거나 빠지지 않는지 — 페이지를 이어 붙인 결과가 넣은 것과 정확히 같은지로 확인한다.
        //
        // **이 테스트가 id 2차 정렬을 잠그지는 못한다**(실측). `OrderByCreatedAtDescIdDesc` 에서 `IdDesc`
        // 를 떼도 그대로 통과한다 — 인덱스가 (user_id, created_at) 이고 InnoDB 는 그 뒤에 PK 를 붙이므로,
        // 지금 플랜(인덱스 역순 스캔)에서는 id 내림차순이 우연히 따라온다. tie-break 를 지키는 것은 이
        // 테스트가 아니라 쿼리의 ORDER BY 자체이고, 인덱스나 플랜이 바뀌면 조용히 깨질 수 있는 자리다.
        // 그래도 이 테스트는 남긴다 — 페이지네이션 계약(최신순·중복·누락)은 여기서만 회귀를 잡는다.
        List<Long> saved = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            saved.add(given(owner, (long) i, 0).getId());
        }

        List<Long> pagedThrough = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            String body = mockMvc.perform(
                            get(URL).with(as(owner)).param("page", String.valueOf(page)).param("size", "2"))
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
        String owner = newOwner();
        Notification target = given(owner, 5L, 0);
        given(owner, 6L, 10);

        mockMvc.perform(patch(READ_URL, target.getId()).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        // 두 번째 요청도 성공이다 — 사용자가 원한 상태가 이미 이뤄져 있고, 개수도 더 줄지 않는다.
        mockMvc.perform(patch(READ_URL, target.getId()).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        mockMvc.perform(get(URL).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications[1].read").value(true))
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    /**
     * 남의 알림은 없는 알림과 <b>똑같이 404</b> 다 — 이 이슈(#280)의 핵심 계약이다.
     *
     * <p>403 으로 나누면 "그 id 는 존재한다" 를 알려주는 셈이라 id 를 훑어 남의 알림 존재를 확인할 수 있다.
     *
     * <p>예전에는 헤더만 바꿔도 소유자가 바뀌어 이 격리가 자칭 하나로 뚫렸다. 지금은 <b>로그인 사용자를
     * 바꿔야</b> 다른 사람이 되고, 그 값은 요청이 정할 수 없다.
     */
    @Test
    void 남의_알림은_없는_알림과_똑같이_404다() throws Exception {
        String stranger = newOwner();
        String owner = newOwner();
        Notification othersNotification = given(owner, 7L, 0);

        mockMvc.perform(patch(READ_URL, othersNotification.getId()).with(as(stranger)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOTIFICATION-002"))
                .andExpect(jsonPath("$.detail").value("요청한 알림을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(patch(READ_URL, 999_999_999L).with(as(stranger)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION-002"));

        // 남의 목록에도 보이지 않는다. 그리고 그대로 안 읽음이어야 한다 — 404 를 준 뒤 조용히 고쳐놓으면 최악이다.
        mockMvc.perform(get(URL).with(as(stranger)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications.length()").value(0));
        mockMvc.perform(get(URL).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test
    void 전체_읽음은_읽을_것이_없어도_성공한다() throws Exception {
        String owner = newOwner();
        given(owner, 1L, 0);
        given(owner, 2L, 10);
        given(owner, 3L, 20);

        mockMvc.perform(post(READ_ALL_URL).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        mockMvc.perform(post(READ_ALL_URL).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        mockMvc.perform(get(URL).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications[0].read").value(true))
                .andExpect(jsonPath("$.data.notifications[2].read").value(true))
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    void 전체_읽음은_남의_알림을_건드리지_않는다() throws Exception {
        String mine = newOwner();
        String yours = newOwner();
        given(mine, 1L, 0);
        given(yours, 2L, 0);

        mockMvc.perform(post(READ_ALL_URL).with(as(mine)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        mockMvc.perform(get(URL).with(as(yours)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    /**
     * 인증 없이 부르면 401 이다.
     *
     * <p>예전에 이 자리에 있던 것은 "게스트 헤더가 비면 400" 이었다. 소유 키가 헤더라 빈 값이
     * {@code @RequestHeader} 를 통과해 도메인까지 닿았기 때문인데, 지금은 <b>헤더 자체가 없다</b> —
     * 주인을 못 밝힌 요청은 컨트롤러에 닿기 전에 {@code SecurityConfig} 가 끊는다(#280).
     */
    @Test
    void 인증_없이_부르면_401이다() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("COMMON-401"))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(post(READ_ALL_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-401"));
    }

    /**
     * 배치가 native 로 넣은 알림이 <b>JPA 조회와 목록 응답에 그대로 보인다</b>.
     *
     * <p>이 클래스에서 제일 값싼 회귀가 아니라 제일 비싼 회귀를 잡는 자리다. {@code saveIfAbsent} 만
     * native 라({@code INSERT ... UUID_TO_BIN(:userId)}) 엔티티 매핑({@code @JdbcTypeCode(BINARY)})을
     * 타지 않는다 — 두 경로가 <b>같은 바이트</b>로 {@code BINARY(16)} 을 쓰는지가 실행으로 확인된 적이
     * 없었다. 어긋나면 배치가 만든 알림을 아무도 못 보는데, 예외도 로그도 남지 않고 <b>목록만 조용히
     * 빈다</b>(#280).
     *
     * <p>그래서 셋을 함께 본다: ① 넣은 행을 JPA 가 같은 소유자로 찾는가 ② 저장된 바이트를 MySQL 이
     * 같은 UUID 로 되읽는가({@code BIN_TO_UUID}) ③ HTTP 목록에 실려 나가는가.
     */
    @Test
    void 배치가_native로_넣은_알림이_내_목록에_그대로_보인다() throws Exception {
        String ownerId = newOwner();
        UUID owner = UUID.fromString(ownerId);
        long courseId = 280_001L;

        boolean created = notificationRepository.saveIfAbsent(Notification.builder()
                .userId(owner)
                .type(NotificationType.TRIP_TOMORROW)
                .courseId(courseId)
                .createdAt(BASE_TIME)
                .build());

        assertTrue(created, "native INSERT 가 새 알림을 만들지 못했다");
        // ① JPA 가 같은 소유자로 찾는다 — UUID_TO_BIN 과 @JdbcTypeCode(BINARY) 의 바이트가 같아야만 걸린다.
        assertEquals(
                1,
                notificationRepository.findByOwner(owner, PageRequest.of(0, 10)).getTotalElements(),
                "native 로 넣은 알림을 JPA 조회가 못 찾는다 — 두 경로의 BINARY(16) 표현이 다르다");
        // ② MySQL 이 그 바이트를 같은 UUID 로 되읽는다. 바이트 순서가 뒤집혔다면 여기서 다른 값이 나온다.
        // 주인으로도 좁힌다 — courseId 만으로 찾으면 같은 DB 로 다시 돌릴 때 앞 실행의 행까지 걸려
        // queryForObject 가 "행이 둘" 로 터진다.
        assertEquals(
                ownerId,
                jdbcTemplate.queryForObject(
                        "SELECT BIN_TO_UUID(user_id) FROM notification WHERE course_id = ? AND user_id = UUID_TO_BIN(?)",
                        String.class,
                        courseId,
                        ownerId));

        // ③ 그리고 실제로 화면에 실려 나간다.
        mockMvc.perform(get(URL).with(as(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications.length()").value(1))
                .andExpect(jsonPath("$.data.notifications[0].courseId").value((int) courseId))
                .andExpect(jsonPath("$.data.unreadCount").value(1));
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
        // 어노테이션의 SecurityContext 는 워커 스레드에 따라오지 않는다 — 요청마다 인증을 싣는다.
        // 그래서 소유자도 어노테이션이 아니라 여기서 만든다.
        String owner = UUID.randomUUID().toString();
        Notification target = given(owner, null, 0);
        int attempts = 2;
        CyclicBarrier barrier = new CyclicBarrier(attempts);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                results.add(executor.submit(() -> {
                    barrier.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    return mockMvc.perform(patch(READ_URL, target.getId()).with(loginAs(UUID.fromString(owner))))
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
        UUID owner = UUID.randomUUID();
        Notification target = given(owner.toString(), null, 0);
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
                            notificationRepository.markRead(owner, target.getId(), BASE_TIME.plusHours(1)));
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
