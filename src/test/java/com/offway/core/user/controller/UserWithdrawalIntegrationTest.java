package com.offway.core.user.controller;

import com.offway.core.notification.domain.Notification;
import com.offway.core.notification.domain.NotificationType;
import com.offway.core.notification.repository.NotificationRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.CourseShare;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.Density;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.itinerary.domain.SlotDisplay;
import com.offway.core.itinerary.domain.SlotKind;
import com.offway.core.itinerary.domain.TimeOfDay;
import com.offway.core.itinerary.domain.TripOutcome;
import com.offway.core.itinerary.domain.VisitOutcome;
import com.offway.core.itinerary.repository.CourseJpaRepository;
import com.offway.core.itinerary.repository.CourseShareRepository;
import com.offway.core.itinerary.repository.TripOutcomeJpaRepository;
import com.offway.core.leave.domain.LeaveBalance;
import com.offway.core.leave.domain.LeaveUsage;
import com.offway.core.leave.domain.StartDayLeave;
import com.offway.core.leave.repository.LeaveBalanceJpaRepository;
import com.offway.core.leave.repository.LeaveUsageJpaRepository;
import com.offway.core.transport.domain.TransportMode;
import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.infrastructure.apple.StubAppleAccountLink;
import org.springframework.context.annotation.Primary;
import com.offway.core.user.infrastructure.social.StubSocialIdentityVerifier;
import com.offway.core.user.repository.UserJpaRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 회원 탈퇴 — 무엇이 지워지고 무엇이 남는지를 계약으로 고정한다.
 *
 * <p>개인정보처리방침이 약속한 동작이라 "대충 지워진다" 로는 부족하다. 남는 것(공유 링크)도 의도된
 * 선택이므로 함께 잠근다.
 *
 * <p><b>지울 대상은 access 토큰이 정한다(#280).</b> 코스·연차·후기가 전부 {@code user_id} 로 묶여 있어,
 * 요청이 무엇을 들고 오든 지워지는 것은 인증으로 확인된 그 사용자의 데이터뿐이다. 그래서 이 클래스의 두 축은
 * <b>내 데이터는 빠짐없이 지워진다</b> 와 <b>남의 데이터는 무엇을 실어 보내도 안 지워진다</b> 다.
 *
 * <p><b>클래스 레벨 트랜잭션 롤백에 기대지 않는다</b>(빠뜨린 것이 아니다). 탈퇴는 이벤트 리스너가 발행자
 * 트랜잭션에 참여해 한 덩어리로 커밋되는 것이 핵심인데, 테스트가 바깥에서 트랜잭션을 열어 롤백해 버리면
 * 그 경계가 테스트 트랜잭션에 흡수돼 검증하려던 성질이 사라진다. 대신 시나리오마다 새 사용자로 격리한다
 * — {@code AuthIntegrationTest} 도 같은 이유로 같은 방식이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserWithdrawalIntegrationTest {

    private static final String WITHDRAW_URL = "/api/v1/users/me";
    private static final String REISSUE_URL = "/api/v1/auth/reissue";
    private static final String CALLBACK_URL = "/api/v1/auth/callback/google";
    private static final String SHARED_COURSE_URL = "/api/v1/public/courses/%s";
    private static final String BEARER = "Bearer ";

    /**
     * 소유 키였던 옛 헤더. 서버는 더 이상 읽지 않지만, <b>실어 보내도 아무 일이 없다</b>는 것이 이 이슈가
     * 닫은 공격의 핵심이라 그 값을 만들어 보내는 테스트가 남아 있다.
     */
    private static final String LEGACY_GUEST_HEADER = "X-Guest-Id";

    @TestConfiguration
    static class SocialStubConfiguration {

        @Bean
        StubSocialIdentityVerifier stubSocialIdentityVerifier() {
            return new StubSocialIdentityVerifier();
        }

        /** Apple 토큰 교환·해제 외부 경계 — appleid.apple.com 을 실제로 부르지 않는다(#287). */
        @Bean
        @Primary
        StubAppleAccountLink stubAppleAccountLink() {
            return new StubAppleAccountLink();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubSocialIdentityVerifier socialIdentityVerifier;

    @Autowired
    private StubAppleAccountLink appleAccountLink;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private CourseJpaRepository courseJpaRepository;

    @Autowired
    private CourseShareRepository courseShareRepository;

    @Autowired
    private LeaveBalanceJpaRepository leaveBalanceJpaRepository;

    @Autowired
    private LeaveUsageJpaRepository leaveUsageJpaRepository;

    @Autowired
    private TripOutcomeJpaRepository tripOutcomeJpaRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    // ── 계정 ──────────────────────────────────────────────────

    @Test
    void 탈퇴하면_200과_안내_문구가_내려간다() throws Exception {
        Session session = login();

        mockMvc.perform(withdraw(session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.detail").value("탈퇴 처리되었습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertTrue(userJpaRepository.findById(session.userId()).isEmpty());
    }

    @Test
    void 탈퇴하면_refresh로_재발급할_수_없다() throws Exception {
        Session session = login();

        mockMvc.perform(withdraw(session.accessToken())).andExpect(status().isOk());

        mockMvc.perform(post(REISSUE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"%s\"}".formatted(session.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER-003"));
    }

    @Test
    void 이미_탈퇴한_계정의_토큰으로_다시_부르면_401_USER_006() throws Exception {
        // access 토큰은 무상태라 탈퇴 후에도 만료(1시간)까지 서명 검증을 통과한다. 그 창을 막지 않으면
        // 없는 계정에 삭제가 또 돌아 200 이 나가고, 앱은 두 번째 탈퇴도 성공했다고 오해한다.
        Session session = login();
        mockMvc.perform(withdraw(session.accessToken())).andExpect(status().isOk());

        mockMvc.perform(withdraw(session.accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER-006"));
    }

    @Test
    void 인증_없이는_탈퇴할_수_없다() throws Exception {
        mockMvc.perform(delete(WITHDRAW_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-401"));
    }

    @Test
    void 탈퇴_후_같은_소셜_신원으로_다시_가입하면_새_사용자다() throws Exception {
        // provider 신원 매핑까지 지우지 않으면 UNIQUE 가 남아 없는 사용자를 가리키는 신원에 붙는다.
        String providerUserId = "sub-" + UUID.randomUUID();
        Session first = login(providerUserId);
        mockMvc.perform(withdraw(first.accessToken())).andExpect(status().isOk());

        Session second = login(providerUserId);

        assertFalse(first.userId().equals(second.userId()), "탈퇴 후 재가입은 새 계정이어야 한다");
        mockMvc.perform(withdraw(second.accessToken())).andExpect(status().isOk());
    }

    // ── 내 데이터는 빠짐없이 지워진다 ───────────────────────────

    /**
     * <b>건너뛰는 길이 없다</b>(#280). 예전에는 지울 대상을 로그인 때 기록해 둔 기기 연결이 정해서, 그 기록이
     * 없으면 계정만 지워지고 코스·연차가 주인 없이 남았다. 이제 소유 키가 탈퇴하는 본인이라 항상 닿는다.
     *
     * <p><b>알림도 함께 본다.</b> 알림은 코스·연차보다 늦게 생겨(#263) 탈퇴 정리에 끼지 못했고, 이 전환에서야
     * 리스너가 붙었다. FK 를 두지 않는 규약이라 DB 가 대신 지워주지 않으므로, 빠지면 알림 행이 <b>없는
     * 사용자를 가리킨 채</b> 남는다. 알림 본문에는 여행 일정이 담긴다.
     */
    @Test
    void 탈퇴하면_내_코스와_연차와_후기가_함께_지워진다() throws Exception {
        Session session = login();
        Long courseId = saveCourse(session.userId());
        seedLeave(session.userId());
        seedOutcome(session.userId(), courseId);
        seedNotification(session.userId(), courseId);

        mockMvc.perform(withdraw(session.accessToken())).andExpect(status().isOk());

        assertTrue(courseJpaRepository.findById(courseId).isEmpty(), "코스가 남았다");
        assertTrue(leaveBalanceJpaRepository.findByUserId(session.userId()).isEmpty(), "연차 설정이 남았다");
        assertTrue(
                leaveUsageJpaRepository
                        .findByUserIdOrderByUsedOnDescIdDesc(session.userId())
                        .isEmpty(),
                "연차 내역이 남았다");
        // 후기는 리스너가 지우는 것 중 하나인데 오래 검증에서 빠져 있었다. 코스와 다른 테이블이라
        // 코스 삭제가 통과해도 이쪽만 조용히 남을 수 있다.
        assertTrue(tripOutcomeJpaRepository.findAnsweredCourseIds(session.userId()).isEmpty(), "여행 후기가 남았다");
        assertEquals(0, notificationRepository.countUnread(session.userId()), "알림이 남았다");
    }

    // ── 남의 데이터는 안 지워진다 ──────────────────────────────

    @Test
    void 탈퇴는_남의_코스와_연차와_후기를_건드리지_않는다() throws Exception {
        Session mine = login();
        Session other = login();
        Long otherCourse = saveCourse(other.userId());
        seedLeave(other.userId());
        seedOutcome(other.userId(), otherCourse);
        seedNotification(other.userId(), otherCourse);

        mockMvc.perform(withdraw(mine.accessToken())).andExpect(status().isOk());

        assertTrue(userJpaRepository.findById(other.userId()).isPresent(), "남의 계정이 지워졌다");
        assertEquals(otherCourse, courseJpaRepository.findById(otherCourse).orElseThrow().getId());
        assertFalse(leaveBalanceJpaRepository.findByUserId(other.userId()).isEmpty(), "남의 연차 설정이 지워졌다");
        // 지우는 축만 후기를 확인하면 "남의 후기는 안 지워지는가" 가 빈다 — 개인정보 관점에서 더 아픈 쪽이다.
        assertFalse(tripOutcomeJpaRepository.findAnsweredCourseIds(other.userId()).isEmpty(), "남의 후기가 지워졌다");
        assertEquals(1, notificationRepository.countUnread(other.userId()), "남의 알림이 지워졌다");
    }

    /**
     * 이 이슈가 닫은 공격을 그대로 재현한다 — <b>이제는 아무 일도 일어나지 않아야 한다</b>.
     *
     * <p>예전 구조는 소유 키가 요청 헤더({@code X-Guest-Id})였다. 일회용 소셜 계정으로 가입하며 피해자의 키를
     * 로그인 콜백에 실어 두면, 그 계정을 탈퇴시키는 것만으로 피해자의 코스·연차·후기가 지워졌다. 임시 대책을
     * 두 번 얹었지만(탈퇴가 헤더를 안 받게 · 서버가 연결을 기록) 자리를 옮겼을 뿐이었다.
     *
     * <p>지금은 파괴가 <b>인증된 주체</b>에 묶여 있어, 남의 계정으로 로그인하지 않는 한 남의 데이터에 닿을 수
     * 없다. 그래서 공격자가 <b>로그인과 탈퇴 양쪽에</b> 피해자의 식별자를 실어 보내도 결과가 바뀌지 않는다.
     *
     * <p>헤더는 서버가 더 이상 읽지 않으므로 이 단언은 지금 자명하다. 그래도 남긴다 — 누군가 요청 값으로 대상을
     * 정하는 길을 다시 열면 <b>여기가 먼저 빨개진다</b>. 그것이 이 테스트의 값어치다.
     */
    @Test
    void 남의_식별자를_요청에_실어_로그인하고_탈퇴해도_남의_데이터는_안_지워진다() throws Exception {
        Session victim = login();
        Long victimCourse = saveCourse(victim.userId());
        seedLeave(victim.userId());
        seedOutcome(victim.userId(), victimCourse);
        // 공격자는 일회용 계정으로 가입하며 피해자의 식별자를 옛 소유 헤더에 실어 보낸다.
        Session attacker = loginClaiming(victim.userId());

        mockMvc.perform(withdraw(attacker.accessToken()).header(LEGACY_GUEST_HEADER, victim.userId()))
                .andExpect(status().isOk());

        assertTrue(userJpaRepository.findById(attacker.userId()).isEmpty(), "공격자 계정은 지워져야 한다");
        assertTrue(userJpaRepository.findById(victim.userId()).isPresent(), "피해자의 계정이 지워졌다");
        assertTrue(courseJpaRepository.findById(victimCourse).isPresent(), "피해자의 코스가 지워졌다");
        assertFalse(leaveBalanceJpaRepository.findByUserId(victim.userId()).isEmpty(), "피해자의 연차 설정이 지워졌다");
        assertFalse(
                tripOutcomeJpaRepository.findAnsweredCourseIds(victim.userId()).isEmpty(), "피해자의 후기가 지워졌다");
    }

    // ── Apple 연결 해제 (#287) ─────────────────────────────────

    /** 실물과 같은 후보 순서 — 웹(Service ID)이 먼저, 네이티브(Bundle ID)가 다음이다. */
    private static final String SERVICE_ID = "com.nth.offway.service";

    private static final String BUNDLE_ID = "com.nth.offway";

    /**
     * <b>코드를 한 번만 쓴다.</b> {@code authorizationCode} 는 1회용이라, 틀린 클라이언트로 먼저 시도하면
     * Apple 이 코드를 살려 둔다는 보장이 없다 — 그러면 맞는 쪽으로 다시 시도해도 늦고, 갱신 토큰을 영영 못 받아
     * 탈퇴해도 Apple 연결이 남는다. 이 PR 이 하려는 일이 정확히 그것이라 추측으로 한 번을 낭비할 수 없다.
     *
     * <p>후보 목록의 <b>뒤쪽</b>을 {@code aud} 로 준다 — 앞쪽을 주면 순서대로 시도해도 우연히 통과해서
     * 이 테스트가 아무것도 못 잡는다.
     */
    @Test
    void 검증된_aud_가_있으면_그_클라이언트로만_교환한다() throws Exception {
        appleAccountLink.reset();
        appleAccountLink.exchangesTo("apple-refresh-token");

        loginWithAppleFrom("apple-auth-code", BUNDLE_ID);

        assertEquals(List.of(BUNDLE_ID), appleAccountLink.exchangedClientIds(), "코드를 한 번만 써야 한다");
    }

    /**
     * 발급한 클라이언트가 해제까지 <b>그대로</b> 흘러야 한다.
     *
     * <p>Apple 은 발급 때와 다른 클라이언트로 서명하면 해제를 거절한다. 로그인에서 고른 값이 저장되지 않거나
     * 탈퇴가 다른 후보를 집으면, 우리 데이터는 지워지고 Apple 목록에만 남는다 — 심사가 보는 그 상태다.
     */
    @Test
    void 로그인에서_고른_클라이언트로_해제한다() throws Exception {
        appleAccountLink.reset();
        appleAccountLink.exchangesTo("apple-refresh-token");
        appleAccountLink.revokeSucceeds();
        Session session = loginWithAppleFrom("apple-auth-code", BUNDLE_ID);

        mockMvc.perform(withdraw(session.accessToken())).andExpect(status().isOk());

        assertEquals(List.of(BUNDLE_ID), appleAccountLink.revokedClientIds(), "발급한 클라이언트로 끊어야 한다");
    }

    /**
     * {@code aud} 를 모르면 예전처럼 후보를 돈다 — 아무것도 안 하는 것보다는 낫다.
     *
     * <p>{@code aud} 를 안 싣는 옛 경로가 남아 있어서다. 이 경우에만 코드 소진 위험을 감수한다.
     */
    @Test
    void aud_를_모르면_후보를_순서대로_시도한다() throws Exception {
        appleAccountLink.reset();
        appleAccountLink.exchangeFails();

        loginWithApple("apple-auth-code");

        assertEquals(List.of(SERVICE_ID, BUNDLE_ID), appleAccountLink.exchangedClientIds());
    }

    /**
     * Apple 로 로그인하며 {@code authorizationCode} 를 함께 보낸다.
     *
     * <p>이 코드는 <b>1회용·5분</b>이라 탈퇴 시점에는 이미 없다. 그래서 로그인 그 순간에 refresh 토큰으로
     * 바꿔 두지 않으면 연결을 끊을 방법이 영영 사라진다.
     */
    private Session loginWithApple(String authorizationCode) throws Exception {
        socialIdentityVerifier.respondWith(AuthProvider.APPLE, "sub-" + UUID.randomUUID(), "세빈", null);
        return performAppleLogin(authorizationCode);
    }

    /** 검증된 {@code aud} 가 있는 로그인 — 어느 클라이언트로 발급된 토큰인지 서버가 아는 상황(#287). */
    private Session loginWithAppleFrom(String authorizationCode, String audience) throws Exception {
        socialIdentityVerifier.respondWithAudience(
                AuthProvider.APPLE, "sub-" + UUID.randomUUID(), "세빈", null, audience);
        return performAppleLogin(authorizationCode);
    }

    /** 신원 stub 은 호출자가 이미 정했다 — 여기서는 콜백만 친다. */
    private Session performAppleLogin(String authorizationCode) throws Exception {
        String body = authorizationCode == null
                ? "{\"accessToken\": \"any-id-token\"}"
                : "{\"accessToken\": \"any-id-token\", \"authorizationCode\": \"%s\"}".formatted(authorizationCode);
        String response = mockMvc.perform(post("/api/v1/auth/callback/apple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String accessToken = JsonPath.read(response, "$.data.accessToken");
        return new Session(
                userIdOf(accessToken), accessToken, JsonPath.read(response, "$.data.refreshToken"));
    }

    @Test
    void 탈퇴하면_Apple_연결을_끊는다() throws Exception {
        appleAccountLink.reset();
        appleAccountLink.exchangesTo("apple-refresh-token");
        appleAccountLink.revokeSucceeds();
        Session session = loginWithApple("apple-auth-code");

        mockMvc.perform(withdraw(session.accessToken())).andExpect(status().isOk());

        assertEquals(List.of("apple-refresh-token"), appleAccountLink.revokedTokens(), "로그인 때 받아 둔 토큰으로 끊어야 한다");
    }

    @Test
    void 연결_해제가_실패해도_탈퇴는_끝난다() throws Exception {
        // Apple 이 흔들린다고 계정을 못 지우면, 지울 권리가 외부 서비스 상태에 묶인다. 못 끊은 것은
        // 사용자가 Apple 설정에서 직접 정리할 수 있고(TN3194), 서버는 사유를 로그로 남긴다.
        appleAccountLink.reset();
        appleAccountLink.exchangesTo("apple-refresh-token");
        appleAccountLink.revokeFails();
        Session session = loginWithApple("apple-auth-code");

        mockMvc.perform(withdraw(session.accessToken())).andExpect(status().isOk());

        assertTrue(userJpaRepository.findById(session.userId()).isEmpty(), "계정은 지워져야 한다");
        assertEquals(1, appleAccountLink.revokedTokens().size(), "시도는 했어야 한다");
    }

    @Test
    void 자격이_없으면_로그인도_탈퇴도_그대로_된다() throws Exception {
        // .p8 없이도 뜨고 로그인되는 것이 이 레포의 불변식이다(로컬 실행성). 교환이 실패하면
        // 저장할 토큰이 없고, 탈퇴는 해제를 건너뛴다.
        appleAccountLink.reset();
        appleAccountLink.exchangeFails();
        Session session = loginWithApple("apple-auth-code");

        mockMvc.perform(withdraw(session.accessToken())).andExpect(status().isOk());

        assertTrue(userJpaRepository.findById(session.userId()).isEmpty(), "계정은 지워져야 한다");
        assertTrue(appleAccountLink.revokedTokens().isEmpty(), "끊을 토큰이 없으면 부르지 않는다");
    }

    @Test
    void authorizationCode_없이_로그인해도_지금과_같다() throws Exception {
        // 옛 앱이다. 로그인은 그대로 되고 연결 해제만 못 한다 — 소급해서 채울 수 없어 정상 경로다.
        appleAccountLink.reset();
        appleAccountLink.exchangesTo("apple-refresh-token");
        Session session = loginWithApple(null);

        mockMvc.perform(withdraw(session.accessToken())).andExpect(status().isOk());

        assertTrue(appleAccountLink.revokedTokens().isEmpty(), "코드를 안 보냈으면 교환도 해제도 없다");
    }

    @Test
    void 구글로_로그인하면_Apple_을_부르지_않는다() throws Exception {
        // provider 를 안 가리면 카카오·구글 탈퇴마다 Apple 을 두드린다 — 쓸데없는 외부 호출이고,
        // 그 실패 로그가 쌓이면 진짜 Apple 실패를 못 알아본다.
        appleAccountLink.reset();
        appleAccountLink.exchangesTo("apple-refresh-token");
        Session session = login();

        mockMvc.perform(withdraw(session.accessToken())).andExpect(status().isOk());

        assertTrue(appleAccountLink.revokedTokens().isEmpty(), "구글 사용자에게 Apple 해제를 시도했다");
    }

    // ── 공유 링크 ─────────────────────────────────────────────

    @Test
    void 탈퇴로_코스가_지워지면_이미_뿌린_공유_링크는_410이다() throws Exception {
        // 404 로 만들지 않는다 — "링크를 잘못 옮겨 적었다" 와 구분되지 않아 받은 사람이 상황을 알 수 없다.
        // 410 은 "있었는데 게시자가 지웠다" 를 정확히 말한다.
        Session session = login();
        Long courseId = saveCourse(session.userId());
        String shareToken = courseShareRepository
                .save(CourseShare.issue(courseId, LocalDateTime.now()))
                .getShareToken();

        mockMvc.perform(withdraw(session.accessToken())).andExpect(status().isOk());

        mockMvc.perform(get(SHARED_COURSE_URL.formatted(shareToken)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("ITINERARY-009"));
    }

    // ── 헬퍼 ──────────────────────────────────────────────────

    private record Session(UUID userId, String accessToken, String refreshToken) {}

    private Session login() throws Exception {
        return login("sub-" + UUID.randomUUID());
    }

    /** 소셜 로그인 한 번 — <b>게스트 헤더를 보내지 않는다</b>. 로그인은 더 이상 기기를 잇지 않는다(#280). */
    private Session login(String providerUserId) throws Exception {
        return login(providerUserId, post(CALLBACK_URL));
    }

    /** 옛 소유 헤더에 남의 식별자를 실어 로그인한다 — 서버가 그 값을 쓰지 않는다는 것을 보이기 위한 경로다. */
    private Session loginClaiming(UUID victimUserId) throws Exception {
        return login("sub-" + UUID.randomUUID(), post(CALLBACK_URL).header(LEGACY_GUEST_HEADER, victimUserId));
    }

    private Session login(String providerUserId, MockHttpServletRequestBuilder request) throws Exception {
        socialIdentityVerifier.respondWith(AuthProvider.GOOGLE, providerUserId, "세빈", null);
        String response = mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\": \"any-id-token\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String accessToken = JsonPath.read(response, "$.data.accessToken");
        return new Session(userIdOf(accessToken), accessToken, JsonPath.read(response, "$.data.refreshToken"));
    }

    /** 탈퇴 — 지울 대상을 지목할 자리가 없다. 대상은 이 access 토큰이 정한다. */
    private MockHttpServletRequestBuilder withdraw(String accessToken) {
        return delete(WITHDRAW_URL).header(HttpHeaders.AUTHORIZATION, BEARER + accessToken);
    }

    private Long saveCourse(UUID userId) {
        Slot slot = Slot.of(
                1,
                TimeOfDay.MORNING,
                SlotKind.SIGHT,
                "c1",
                "장소1",
                37.50,
                128.60,
                0,
                new SlotDisplay(null, null, null, null));
        Course course = Course.ownedBy(
                userId,
                16L,
                Density.PACKED,
                TransportMode.CAR,
                List.of(DaySchedule.of(1, List.of(slot))),
                LocalDate.now().plusDays(7),
                1,
                null,
                StartDayLeave.DEFAULT);
        return courseJpaRepository.save(course).getId();
    }

    private void seedLeave(UUID userId) {
        leaveBalanceJpaRepository.save(LeaveBalance.of(userId, 15.0));
        leaveUsageJpaRepository.save(LeaveUsage.manual(userId, LocalDate.now(), 1.0, "테스트"));
    }

    /** 여행 후기 한 건 — 탈퇴가 지우는 데이터 중 코스와 다른 테이블에 있는 쪽이다. */
    private void seedOutcome(UUID userId, Long courseId) {
        tripOutcomeJpaRepository.save(TripOutcome.of(userId, courseId, VisitOutcome.VISITED, LocalDate.now()));
    }

    /** access 토큰(JWT) payload 의 sub 이 사용자 식별자다. */
    private static UUID userIdOf(String accessToken) {
        String payload = new String(java.util.Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]));
        return UUID.fromString(JsonPath.read(payload, "$.sub"));
    }

    /** 알림 한 건 — 탈퇴가 알림까지 지우는지 보려면 지울 것이 있어야 한다. */
    private void seedNotification(UUID userId, Long courseId) {
        notificationRepository.save(Notification.builder()
                .userId(userId)
                .type(NotificationType.TRIP_TOMORROW)
                .courseId(courseId)
                .createdAt(LocalDateTime.now())
                .build());
    }
}
