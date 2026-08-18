package com.offway.core.user.controller;

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
import com.offway.core.leave.domain.StartDayLeave;
import com.offway.core.itinerary.domain.CourseShare;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.Density;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.itinerary.domain.TripOutcome;
import com.offway.core.itinerary.domain.VisitOutcome;
import com.offway.core.itinerary.repository.TripOutcomeJpaRepository;
import com.offway.core.itinerary.domain.SlotDisplay;
import com.offway.core.itinerary.domain.SlotKind;
import com.offway.core.itinerary.domain.TimeOfDay;
import com.offway.core.itinerary.repository.CourseJpaRepository;
import com.offway.core.itinerary.repository.CourseShareRepository;
import com.offway.core.leave.domain.LeaveBalance;
import com.offway.core.leave.domain.LeaveUsage;
import com.offway.core.leave.repository.LeaveBalanceJpaRepository;
import com.offway.core.leave.repository.LeaveUsageJpaRepository;
import com.offway.core.transport.domain.TransportMode;
import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.repository.UserGuestLinkRepository;
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

/**
 * 회원 탈퇴 — 무엇이 지워지고 무엇이 남는지를 계약으로 고정한다.
 *
 * <p>개인정보처리방침이 약속한 동작이라 "대충 지워진다" 로는 부족하다. 남는 것(공유 링크)도 의도된
 * 선택이므로 함께 잠근다.
 *
 * <p><b>클래스 레벨 트랜잭션 롤백에 기대지 않는다</b>(빠뜨린 것이 아니다). 탈퇴는 이벤트 리스너가 발행자
 * 트랜잭션에 참여해 한 덩어리로 커밋되는 것이 핵심인데, 테스트가 바깥에서 트랜잭션을 열어 롤백해 버리면
 * 그 경계가 테스트 트랜잭션에 흡수돼 검증하려던 성질이 사라진다. 대신 시나리오마다 고유 UUID 로 격리한다
 * — {@code AuthIntegrationTest} 도 같은 이유로 같은 방식이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserWithdrawalIntegrationTest {

    private static final String WITHDRAW_URL = "/api/v1/users/me";
    private static final String REISSUE_URL = "/api/v1/auth/reissue";
    private static final String CALLBACK_URL = "/api/v1/auth/callback/google";
    private static final String SHARED_COURSE_URL = "/api/v1/public/courses/%s";
    private static final String GUEST_HEADER = "X-Guest-Id";
    private static final String BEARER = "Bearer ";

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
    private UserGuestLinkRepository userGuestLinkRepository;

    @Autowired
    private TripOutcomeJpaRepository tripOutcomeJpaRepository;

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
        socialIdentityVerifier.respondWith(AuthProvider.GOOGLE, providerUserId, "세빈", null);
        Session first = login(providerUserId);
        mockMvc.perform(withdraw(first.accessToken())).andExpect(status().isOk());

        Session second = login(providerUserId);

        assertFalse(first.userId().equals(second.userId()), "탈퇴 후 재가입은 새 계정이어야 한다");
        mockMvc.perform(withdraw(second.accessToken())).andExpect(status().isOk());
    }

    // ── 게스트 키로 묶인 데이터 ────────────────────────────────

    @Test
    void 탈퇴하면_저장한_코스와_연차와_후기가_함께_지워진다() throws Exception {
        Session session = login();
        Long courseId = saveCourse(session.guestId());
        seedLeave(session.guestId());
        seedOutcome(session.guestId(), courseId);

        mockMvc.perform(withdraw(session.accessToken())).andExpect(status().isOk());

        assertTrue(courseJpaRepository.findById(courseId).isEmpty(), "코스가 남았다");
        assertTrue(leaveBalanceJpaRepository.findByGuestId(session.guestId()).isEmpty(), "연차 설정이 남았다");
        assertTrue(leaveUsageJpaRepository.findByGuestIdOrderByUsedOnDescIdDesc(session.guestId()).isEmpty(), "연차 내역이 남았다");
        // 후기는 리스너가 지우는 넷 중 하나인데 오래 검증에서 빠져 있었다. 코스와 다른 테이블이라
        // 코스 삭제가 통과해도 이쪽만 조용히 남을 수 있다.
        assertTrue(tripOutcomeJpaRepository.findAnsweredCourseIds(session.guestId()).isEmpty(), "여행 후기가 남았다");
    }

    @Test
    void 로그인할_때_기기를_안_이었으면_계정만_지워진다() throws Exception {
        // 남은 한계다. 지울 대상은 로그인 시점의 기록이 정하므로, 그때 헤더를 안 보낸 앱의 코스·연차에는
        // 닿지 못한다. 그렇다고 탈퇴를 막지는 않는다 — 계정을 지울 권리가 옛 로그인에 인질로 잡힌다.
        // 못 지운 사실은 서버가 warn 으로 남긴다.
        String providerUserId = "sub-" + UUID.randomUUID();
        socialIdentityVerifier.respondWith(AuthProvider.GOOGLE, providerUserId, "세빈", null);
        String response = mockMvc.perform(post(CALLBACK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\": \"any-id-token\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String accessToken = JsonPath.read(response, "$.data.accessToken");
        Long courseId = saveCourse("guest-" + UUID.randomUUID());

        mockMvc.perform(withdraw(accessToken)).andExpect(status().isOk());

        assertTrue(userJpaRepository.findById(userIdOf(accessToken)).isEmpty(), "계정은 지워져야 한다");
        assertTrue(courseJpaRepository.findById(courseId).isPresent(), "이은 기기가 없으면 코스에 닿지 못한다");
    }

    /**
     * 헤더로 남의 데이터를 지목할 수 없다 — <b>탈퇴가 헤더를 아예 안 받는다</b>.
     *
     * <p>지울 대상은 로그인 때 기록된 연결이 정한다. 예전에는 요청 헤더가 대상을 정해서, 남의 게스트 키를
     * 적어 보내면 그 사람의 연차·후기가 지워졌다.
     */
    @Test
    void 남의_게스트_키를_헤더로_보내도_남의_데이터는_안_지워진다() throws Exception {
        Session mine = login();
        Session other = login();
        Long otherCourse = saveCourse(other.guestId());
        seedOutcome(other.guestId(), otherCourse);

        mockMvc.perform(withdraw(mine.accessToken()).header(GUEST_HEADER, other.guestId()))
                .andExpect(status().isOk());

        assertTrue(courseJpaRepository.findById(otherCourse).isPresent(), "남의 코스가 지워졌다");
        assertTrue(userJpaRepository.findById(other.userId()).isPresent(), "남의 계정이 지워졌다");
        // 지우는 축만 후기를 확인하면 "남의 후기는 안 지워지는가" 가 빈다 — 개인정보 관점에서 더 아픈 쪽이다.
        assertFalse(tripOutcomeJpaRepository.findAnsweredCourseIds(other.guestId()).isEmpty(), "남의 후기가 지워졌다");
    }

    /**
     * 남의 기기 키를 <b>로그인 헤더로</b> 보내 이어 붙인 뒤 탈퇴해도 그 사람의 데이터는 남는다.
     *
     * <p>위 테스트는 탈퇴 요청에 헤더를 실었을 때를 본다. 이건 다른 경로다 — <b>로그인 때</b> 남의 키를
     * 실으면 그 기기가 내 것으로 기록되고, 그다음 탈퇴는 헤더 없이도 그 기기를 지운다. 탈퇴가 헤더를 안 받는
     * 것만으로는 이 경로가 막히지 않는다.
     *
     * <p>막는 것은 <b>한 게스트 키가 한 사용자에게만 붙는다</b>는 제약이다({@code uk_user_guest_link_guest}).
     * 먼저 로그인한 쪽이 그 기기를 갖고, 뒤에 온 로그인은 가져가지 못한다.
     *
     * <p><b>그래도 로그인 자체는 성공한다.</b> 이을 수 없다고 로그인을 막으면, 남의 키 한 줄을 헤더에 적어
     * 보내는 것으로 그 사람의 가입을 봉쇄할 수 있다.
     *
     * <p>남은 구멍은 <b>피해자가 한 번도 로그인하지 않은 경우</b>다 — 그때는 선점자가 없어 공격자가 그 기기를
     * 가져간다. 헤더가 파괴 범위를 정하는 구조 자체를 없애야 닫히고, 그것이 이슈 #280 이다.
     */
    @Test
    void 남의_기기_키로_로그인한_뒤_탈퇴해도_남의_데이터는_안_지워진다() throws Exception {
        Session victim = login();
        Long victimCourse = saveCourse(victim.guestId());
        seedLeave(victim.guestId());
        // 공격자가 피해자의 게스트 키를 로그인 헤더에 실어 보낸다.
        Session attacker = login("sub-" + UUID.randomUUID(), victim.guestId());

        // 탈퇴 전에 선점을 직접 확인한다 — 여기가 뚫리면 아래 단언들은 이미 늦었다.
        assertEquals(
                victim.userId(),
                userGuestLinkRepository.findByGuestId(victim.guestId()).orElseThrow().getUserId(),
                "공격자가 피해자의 기기를 가져갔다");

        mockMvc.perform(withdraw(attacker.accessToken())).andExpect(status().isOk());

        assertTrue(userJpaRepository.findById(attacker.userId()).isEmpty(), "공격자 계정은 지워져야 한다");
        assertTrue(courseJpaRepository.findById(victimCourse).isPresent(), "피해자의 코스가 지워졌다");
        assertFalse(
                leaveBalanceJpaRepository.findByGuestId(victim.guestId()).isEmpty(), "피해자의 연차 설정이 지워졌다");
        assertTrue(userJpaRepository.findById(victim.userId()).isPresent(), "피해자의 계정이 지워졌다");
    }

    // ── Apple 연결 해제 (#287) ─────────────────────────────────

    /**
     * Apple 로 로그인하며 {@code authorizationCode} 를 함께 보낸다.
     *
     * <p>이 코드는 <b>1회용·5분</b>이라 탈퇴 시점에는 이미 없다. 그래서 로그인 그 순간에 refresh 토큰으로
     * 바꿔 두지 않으면 연결을 끊을 방법이 영영 사라진다.
     */
    private Session loginWithApple(String authorizationCode) throws Exception {
        socialIdentityVerifier.respondWith(AuthProvider.APPLE, "sub-" + UUID.randomUUID(), "세빈", null);
        String body = authorizationCode == null
                ? "{\"accessToken\": \"any-id-token\"}"
                : "{\"accessToken\": \"any-id-token\", \"authorizationCode\": \"%s\"}".formatted(authorizationCode);
        String guest = "guest-" + UUID.randomUUID();
        String response = mockMvc.perform(post("/api/v1/auth/callback/apple")
                        .header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String accessToken = JsonPath.read(response, "$.data.accessToken");
        return new Session(
                userIdOf(accessToken), accessToken, JsonPath.read(response, "$.data.refreshToken"), guest);
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
        Long courseId = saveCourse(session.guestId());
        String shareToken = courseShareRepository
                .save(CourseShare.issue(courseId, LocalDateTime.now()))
                .getShareToken();

        mockMvc.perform(withdraw(session.accessToken())).andExpect(status().isOk());

        mockMvc.perform(get(SHARED_COURSE_URL.formatted(shareToken)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("ITINERARY-009"));
    }

    // ── 헬퍼 ──────────────────────────────────────────────────

    @Test
    void 탈퇴는_다른_사람의_데이터를_건드리지_않는다() throws Exception {
        Session mine = login();
        Session other = login();
        Long otherCourse = saveCourse(other.guestId());

        mockMvc.perform(withdraw(mine.accessToken())).andExpect(status().isOk());

        assertTrue(userJpaRepository.findById(other.userId()).isPresent(), "남의 계정이 지워졌다");
        assertEquals(otherCourse, courseJpaRepository.findById(otherCourse).orElseThrow().getId());
    }

    private record Session(UUID userId, String accessToken, String refreshToken, String guestId) {}

    private Session login() throws Exception {
        String providerUserId = "sub-" + UUID.randomUUID();
        socialIdentityVerifier.respondWith(AuthProvider.GOOGLE, providerUserId, "세빈", null);
        return login(providerUserId);
    }

    private Session login(String providerUserId) throws Exception {
        return login(providerUserId, "guest-" + UUID.randomUUID());
    }

    /**
     * 로그인하며 이 기기를 사용자에게 잇는다.
     *
     * <p>탈퇴가 지울 대상을 여기서 정한다 — 헤더를 안 보내고 로그인하면 그 사용자의 코스·연차는 서버가
     * 찾지 못한다({@link #로그인할_때_기기를_안_이었으면_계정만_지워진다}).
     */
    private Session login(String providerUserId, String guestId) throws Exception {
        socialIdentityVerifier.respondWith(AuthProvider.GOOGLE, providerUserId, "세빈", null);
        String response = mockMvc.perform(post(CALLBACK_URL)
                        .header(GUEST_HEADER, guestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\": \"any-id-token\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String accessToken = JsonPath.read(response, "$.data.accessToken");
        return new Session(
                userIdOf(accessToken), accessToken, JsonPath.read(response, "$.data.refreshToken"), guestId);
    }

    /**
     * 탈퇴 — <b>게스트 헤더를 보내지 않는다</b>. 지울 대상은 로그인 때 기록된 연결이 정한다.
     *
     * <p>헤더를 받지 않으므로 남의 값을 적어 넣을 자리도 없다.
     */
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withdraw(String accessToken) {
        return delete(WITHDRAW_URL).header(HttpHeaders.AUTHORIZATION, BEARER + accessToken);
    }

    private Long saveCourse(String guestId) {
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
                guestId,
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

    private void seedLeave(String guestId) {
        leaveBalanceJpaRepository.save(LeaveBalance.of(guestId, 15.0));
        leaveUsageJpaRepository.save(LeaveUsage.manual(guestId, LocalDate.now(), 1.0, "테스트"));
    }

    /** access 토큰(JWT) payload 의 sub 이 사용자 식별자다. */
    /** 여행 후기 한 건 — 탈퇴가 지우는 넷째 데이터다. */
    private void seedOutcome(String guestId, Long courseId) {
        tripOutcomeJpaRepository.save(TripOutcome.of(guestId, courseId, VisitOutcome.VISITED, LocalDate.now()));
    }

    private static UUID userIdOf(String accessToken) {
        String payload = new String(java.util.Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]));
        return UUID.fromString(JsonPath.read(payload, "$.sub"));
    }
}
