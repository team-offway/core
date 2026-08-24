package com.offway.core.itinerary.controller;

import static com.offway.core.user.config.TestLogins.loginAs;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.testSecurityContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.offway.core.leave.infrastructure.holiday.HolidayClient;
import com.offway.core.leave.infrastructure.holiday.StubHolidayClient;
import com.offway.core.leave.service.LeaveService;
import com.offway.core.user.config.WithLoginUser;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 코스 확정 → 연차 차감(#91) 통합 테스트.
 *
 * <p>DB 격리는 롤백 대신 <b>테스트마다 새 사용자</b>로 한다 — 값 없는 {@link WithLoginUser} 가 매번 다른 UUID 를
 * 넣고, 연차 잔액·사용내역이 사용자 단위로 묶이므로 서로 섞이지 않는다(#280 이전의 고유 게스트 ID 와 같은 역할).
 * 공휴일은 외부 호출이라 stub 으로 격리하고, 캐시가 컨텍스트를 공유하므로 각 테스트가 본문에서 비운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithLoginUser
class CourseLeaveDeductionIntegrationTest {

    private static final String COURSES = "/api/v1/courses";
    private static final String LEAVES = "/api/v1/leaves/me";

    /** {@code SecurityConfig} · {@code JwtAuthenticationFilter} 가 쓰는 권한 이름 — 같은 값이어야 한다. */
    private static final String USER_AUTHORITY = "ROLE_USER";

    /** 2026-08-12(수) 시작 1박2일 — 수·목 모두 평일이라 공휴일이 없으면 2일이 깎인다. */
    private static final LocalDate START = LocalDate.of(2026, 8, 12);

    private static final String TWO_DAY_COURSE = """
            { "regionId": 16, "density": "PACKED", "transport": "CAR", "travelDate": "2026-08-12", "days": [
              { "day": 1, "items": [
                {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c1","title":"장소1","lat":37.50,"lng":128.60,"travelMinutes":0}
              ]},
              { "day": 2, "items": [
                {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c2","title":"장소2","lat":37.51,"lng":128.61,"travelMinutes":0}
              ]}
            ]}""";

    /**
     * 위와 같은 코스인데 <b>첫날을 반차로 시작</b>한다(#284).
     *
     * <p>단위를 차감 요청이 아니라 <b>코스 저장에</b> 싣는다 — 차감은 코스가 아는 값을 쓰지 다시 묻지
     * 않는다(#288). 예전에는 {@code POST /leave-deduction} 본문에 {@code halfDayStart} 를 실었다.
     */
    private static final String TWO_DAY_HALF_DAY_COURSE = """
            { "regionId": 16, "density": "PACKED", "transport": "CAR", "travelDate": "2026-08-12",
              "startDayLeave": "HALF_DAY", "days": [
              { "day": 1, "items": [
                {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c1","title":"장소1","lat":37.50,"lng":128.60,"travelMinutes":0}
              ]},
              { "day": 2, "items": [
                {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c2","title":"장소2","lat":37.51,"lng":128.61,"travelMinutes":0}
              ]}
            ]}""";

    /** 위와 같은 코스인데 <b>첫날을 반반차로 시작</b>한다(#278) — 첫날 소모가 0.25 라 차감이 1.25 다. */
    private static final String TWO_DAY_QUARTER_DAY_COURSE =
            TWO_DAY_HALF_DAY_COURSE.replace("\"HALF_DAY\"", "\"QUARTER_DAY\"");

    /**
     * 첫날이 이동뿐이라 일정에서 빠진 2박3일 코스 — 담긴 날은 둘인데 여행은 8/12~8/14 사흘이다(#159 · #164).
     *
     * <p>{@code travelDays} 로 달력 기간을 함께 보낸다. 이게 없으면 서버는 담긴 날 수(2)를 기간으로 보고
     * 종료일을 하루 이르게 잡는다.
     */
    private static final String FIRST_DAY_EMPTY_COURSE = """
            { "regionId": 16, "density": "PACKED", "transport": "TRANSIT", "travelDate": "2026-08-12",
              "travelDays": 3, "days": [
              { "day": 1, "items": [
                {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c1","title":"장소1","lat":37.50,"lng":128.60,"travelMinutes":0}
              ]},
              { "day": 2, "items": [
                {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c2","title":"장소2","lat":37.51,"lng":128.61,"travelMinutes":0}
              ]}
            ]}""";

    /** 위와 같은 코스인데 {@code travelDays} 를 안 보낸 것 — 이 필드가 생기기 전 클라이언트와 같은 모양이다. */
    private static final String FIRST_DAY_EMPTY_COURSE_WITHOUT_SPAN =
            FIRST_DAY_EMPTY_COURSE.replace("\"travelDays\": 3,", "");

    /** 여행 날짜 없이 저장된 코스 — 이 컬럼이 생기기 전에 저장된 코스와 같은 모양이다. */
    private static final String COURSE_WITHOUT_DATE = """
            { "regionId": 16, "density": "PACKED", "transport": "CAR", "days": [
              { "day": 1, "items": [
                {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c1","title":"장소1","lat":37.50,"lng":128.60,"travelMinutes":0}
              ]}
            ]}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubHolidayClient holidayClient;

    @Autowired
    private LeaveService leaveService;

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        HolidayClient stubHolidayClient() {
            return new StubHolidayClient();
        }
    }


    /** 공휴일 동작을 정하고 캐시를 비운다 — 앞 테스트가 캐시에 남긴 공휴일이 이 시나리오로 새지 않게. */
    private void holidays(Set<LocalDate> holidays) {
        holidayClient.respond((year, month) -> holidays);
        leaveService.evictCache();
    }

    private void setTotalLeave(double totalDays) throws Exception {
        setTotalLeave(totalDays, testSecurityContext());
    }

    private void setTotalLeave(double totalDays, RequestPostProcessor as) throws Exception {
        mockMvc.perform(patch(LEAVES).with(as)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalDays\": " + totalDays + "}"))
                .andExpect(status().isOk());
    }

    private long saveCourse(String body) throws Exception {
        return saveCourse(body, testSecurityContext());
    }

    private long saveCourse(String body, RequestPostProcessor as) throws Exception {
        String saved = mockMvc.perform(post(COURSES).with(as)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(saved, "$.data.courseId")).longValue();
    }

    /**
     * 여행을 다녀왔다고 답한다 — <b>차감의 유일한 입구</b>(#288).
     *
     * <p>예전에는 {@code POST /courses/{id}/leave-deduction} 이 따로 있었다. 여행 <b>전</b>에 깎을 수 있어
     * 취소한 여행의 연차가 묶인 채 남고 홈 모달도 뜨지 않아, 입구를 이쪽 하나로 모았다.
     *
     * <p>차감 단위(종일·반차·반반차)는 보내지 않는다. 코스가 만들어질 때 이미 답한 값을 서버가 쓴다.
     */
    private ResultActions answerVisited(long courseId) throws Exception {
        return answerVisited(courseId, testSecurityContext());
    }

    private ResultActions answerVisited(long courseId, RequestPostProcessor as) throws Exception {
        return mockMvc.perform(post(COURSES + "/{id}/trip-outcome", courseId)
                .with(as)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\": \"VISITED\"}"));
    }

    @Test
    void 코스를_확정하면_평일_수만큼_연차가_차감된다() throws Exception {
        holidays(Set.of());
        setTotalLeave(15.0);
        long courseId = saveCourse(TWO_DAY_COURSE);

        answerVisited(courseId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.totalDays").value(15.0))
                .andExpect(jsonPath("$.data.usedDays").value(2.0))
                .andExpect(jsonPath("$.data.remainingDays").value(13.0))
                .andExpect(jsonPath("$.data.usages.length()").value(1))
                .andExpect(jsonPath("$.data.usages[0].days").value(2.0));
    }

    @Test
    void 첫날이_일정에서_빠져도_사흘치가_차감된다() throws Exception {
        // 8/12(수)~8/14(금) 사흘 여행인데 일정은 이틀치뿐이다. 예전에는 담긴 날 수(2)로 종료일을 세서
        // 8/13 에서 끊겼고, 그만큼 연차가 덜 차감됐다(#164).
        holidays(Set.of());
        setTotalLeave(15.0);
        long courseId = saveCourse(FIRST_DAY_EMPTY_COURSE);

        answerVisited(courseId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedDays").value(3.0))
                .andExpect(jsonPath("$.data.remainingDays").value(12.0));
    }

    @Test
    void 기간을_안_보내면_담긴_날_수로_차감한다() throws Exception {
        // 이 필드가 생기기 전 클라이언트와 같은 동작 — 기존 연동이 깨지지 않는다는 것을 못 박는다.
        // 이 경우 첫날이 빠진 코스는 여전히 하루 덜 차감된다. 그래서 기간을 보내라고 문서에 적었다.
        holidays(Set.of());
        setTotalLeave(15.0);
        long courseId = saveCourse(FIRST_DAY_EMPTY_COURSE_WITHOUT_SPAN);

        answerVisited(courseId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedDays").value(2.0));
    }

    @Test
    void 구간에_공휴일이_끼면_그만큼_덜_차감된다() throws Exception {
        // 서버가 실제로 다시 계산한다는 증거 — 요청은 위 테스트와 똑같고 공휴일만 다른데 결과가 바뀐다.
        holidays(Set.of(START.plusDays(1)));
        setTotalLeave(15.0);
        long courseId = saveCourse(TWO_DAY_COURSE);

        answerVisited(courseId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedDays").value(1.0))
                .andExpect(jsonPath("$.data.remainingDays").value(14.0));
    }

    @Test
    void 반차로_만든_코스는_반차만큼만_차감된다() throws Exception {
        // 사용자가 손해 보던 현재 버그다(#288). 모달 경로가 단위를 종일로 고정해, 반차로 짠 코스도
        // 1.0 이 깎였다 — 매번 0.5 를 더 잃는다. 코스가 아는 값을 쓰면 사라진다.
        holidays(Set.of());
        setTotalLeave(15.0);
        long courseId = saveCourse(TWO_DAY_HALF_DAY_COURSE);

        answerVisited(courseId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedDays").value(1.5))
                .andExpect(jsonPath("$.data.remainingDays").value(13.5));
    }

    @Test
    void 반반차로_만든_코스는_반반차만큼만_차감된다() throws Exception {
        // **이 경로가 실제로 깨져 있었다.** 반반차의 첫날 소모는 0.25 인데(#284) 연차 격자가 0.5 여서,
        // 차감량 1.25 가 자기 검증에 걸려 LEAVE-010 400 이 나갔다 — 앱이 이미 내주는 선택지를 서버가
        // 거절한 것이다(#278). 반차(1.5)와 나란히 둬서 단위별 차감을 한눈에 대조한다.
        holidays(Set.of());
        setTotalLeave(15.0);
        long courseId = saveCourse(TWO_DAY_QUARTER_DAY_COURSE);

        answerVisited(courseId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedDays").value(1.25))
                .andExpect(jsonPath("$.data.remainingDays").value(13.75));
    }

    @Test
    void 같은_여행에_두_번_답해도_차감은_한_번이다() throws Exception {
        holidays(Set.of());
        setTotalLeave(15.0);
        long courseId = saveCourse(TWO_DAY_COURSE);

        answerVisited(courseId).andExpect(status().isOk());
        // 두 번째 답은 409 다 — 차감이 멱등인 것과 별개로, 이미 답한 여행에 다시 답하는 것은 막는다.
        // 예전 POST /leave-deduction 은 200 을 돌려줬다. 그쪽은 "답" 이 아니라 "차감 명령" 이었다.
        answerVisited(courseId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITINERARY-005"));

        mockMvc.perform(get(LEAVES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remainingDays").value(13.0))
                .andExpect(jsonPath("$.data.usages.length()").value(1));
    }

    @Test
    void 남은_연차가_부족해도_차감되고_잔여가_음수가_된다() throws Exception {
        // 결정 #38 — 서버는 막지 않는다. 경고와 확인은 프론트가 맡는다.
        holidays(Set.of());
        setTotalLeave(1.0);
        long courseId = saveCourse(TWO_DAY_COURSE);

        answerVisited(courseId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedDays").value(2.0))
                .andExpect(jsonPath("$.data.remainingDays").value(-1.0));
    }

    @Test
    void 여행_날짜가_없는_코스는_400이다() throws Exception {
        holidays(Set.of());
        long courseId = saveCourse(COURSE_WITHOUT_DATE);

        answerVisited(courseId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("ITINERARY-004"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 남의_코스로는_차감할_수_없다() throws Exception {
        holidays(Set.of());
        long courseId = saveCourse(TWO_DAY_COURSE, loginAs(UUID.randomUUID()));

        answerVisited(courseId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-003"));
    }

    @Test
    void 남의_코스_차감_시도는_내_연차를_건드리지_않는다() throws Exception {
        // 404 만 보면 "거절했다" 까지다. 거절 뒤에 내 잔액이 그대로인지가 소유자 격리의 실질이다.
        holidays(Set.of());
        setTotalLeave(15.0);
        long courseId = saveCourse(TWO_DAY_COURSE, loginAs(UUID.randomUUID()));

        answerVisited(courseId).andExpect(status().isNotFound());

        mockMvc.perform(get(LEAVES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remainingDays").value(15.0))
                .andExpect(jsonPath("$.data.usages.length()").value(0));
    }

    @Test
    void 같은_여행에_동시에_답해도_차감은_하나다() throws Exception {
        // 조회로만 거르면 두 요청이 둘 다 "아직 안 했다" 를 보고 두 번 차감한다 — 유니크 제약이 막는지 확인한다.
        //
        // 인증을 요청 단위로 붙인다: 클래스의 @WithLoginUser 는 현재 스레드 전용이라 아래 풀 스레드엔 안 닿는다.
        // 그래서 이 테스트만 사용자를 직접 만들어 준비·답변을 같은 사람으로 맞춘다.
        holidays(Set.of());
        RequestPostProcessor owner = loginAs(UUID.randomUUID());
        setTotalLeave(15.0, owner);
        long courseId = saveCourse(TWO_DAY_COURSE, owner);

        int threads = 2;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        statuses.add(answerVisited(courseId, owner).andReturn().getResponse().getStatus());
                    } catch (Exception e) {
                        statuses.add(-1);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            Assertions.assertTrue(done.await(20, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        // 하나는 답을 기록하고, 다른 하나는 유니크 제약에 걸려 409 다. 둘 다 200 이면 답이 두 번
        // 기록됐다는 뜻이고, 그러면 차감도 두 번 돌 수 있다.
        Assertions.assertEquals(List.of(200, 409), statuses.stream().sorted().toList(),
                "동시 답변은 하나만 기록돼야 한다. 실제=" + statuses);
        mockMvc.perform(get(LEAVES).with(owner))
                .andExpect(jsonPath("$.data.usedDays").value(2.0))
                .andExpect(jsonPath("$.data.usages.length()").value(1));
    }

    /**
     * 코스 확정으로 생긴 내역은 <b>연차 화면에서 지울 수 없다</b>(#265) — 409 로 끊고 코스 쪽으로 안내한다.
     *
     * <p>지우게 두면 코스는 확정인데 연차는 안 깎인 상태가 남고, 그 행을 전제로 도는 코스 삭제·날짜 변경도
     * 함께 어긋난다. 404 로 감추지도 않는다 — 자기 내역이 화면에 보이는데 "없다" 고 답하면 버그로 읽힌다.
     */
    @Test
    void 코스_확정_내역은_연차_화면에서_지울_수_없다() throws Exception {
        holidays(Set.of());
        setTotalLeave(15.0);
        long courseId = saveCourse(TWO_DAY_COURSE);
        String deducted = answerVisited(courseId)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long usageId = ((Number) JsonPath.read(deducted, "$.data.usages[0].id")).longValue();

        mockMvc.perform(delete(LEAVES + "/usages/{id}", usageId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("LEAVE-014"))
                .andExpect(jsonPath("$.detail").value("코스 확정으로 기록된 연차입니다. 코스에서 차감을 취소해 주세요."))
                .andExpect(jsonPath("$.data").doesNotExist());

        // 차감은 그대로 남는다.
        mockMvc.perform(get(LEAVES))
                .andExpect(jsonPath("$.data.usedDays").value(2.0))
                .andExpect(jsonPath("$.data.usages.length()").value(1));
    }

    // ── 상세 응답의 차감 정보(#317) ──────────────────────────

    @Test
    void 코스_상세가_차감_여부와_차감량을_함께_준다() throws Exception {
        // 앱이 상세를 열 때마다 목록을 병행 조회해 요약의 차감 여부를 찾아 채우고 있었다 —
        // 상세 하나 보는 데 요청이 두 번 나갔다.
        holidays(Set.of());
        // 주인은 클래스의 @WithLoginUser 가 정한다(#280) — 소유 키 헤더를 실어 보내지 않는다.
        setTotalLeave(15.0);
        long courseId = saveCourse(TWO_DAY_COURSE);

        mockMvc.perform(get(COURSES + "/{id}", courseId))
                .andExpect(status().isOk())
                // 차감 전에는 "안 했다" 로 답한다. 값이 아예 없는 것과 구분돼야 화면이 뱃지를 그릴 수 있다.
                .andExpect(jsonPath("$.data.leaveDeducted").value(false))
                .andExpect(jsonPath("$.data.consumedLeaveDays").doesNotExist());

        answerVisited(courseId).andExpect(status().isOk());

        mockMvc.perform(get(COURSES + "/{id}", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.leaveDeducted").value(true))
                .andExpect(jsonPath("$.data.consumedLeaveDays").value(2.0));
    }

    @Test
    void 공유_링크로_여는_코스에는_차감_정보가_없다() throws Exception {
        // **차감은 코스가 아니라 소유자에게 딸린 값이다.** 링크를 받은 사람에게 내리면 남의 연차 사정을
        // 알려주는 셈이 된다. courseId·shareToken 을 걷어내는 것과 같은 이유다(#143).
        holidays(Set.of());
        setTotalLeave(15.0);
        long courseId = saveCourse(TWO_DAY_COURSE);
        answerVisited(courseId).andExpect(status().isOk());

        String detail = mockMvc.perform(get(COURSES + "/{id}", courseId))
                .andExpect(jsonPath("$.data.leaveDeducted").value(true))
                .andReturn().getResponse().getContentAsString();
        String shareToken = JsonPath.read(detail, "$.data.shareToken");

        mockMvc.perform(get("/api/v1/public/courses/{token}", shareToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.leaveDeducted").doesNotExist())
                .andExpect(jsonPath("$.data.consumedLeaveDays").doesNotExist());
    }

    @Test
    void 코스_확정_내역은_연차_화면에서_고칠_수도_없다() throws Exception {
        // 삭제와 같은 관문이다(#267). 수정은 더 위험하다 — 일수를 고치면 코스가 아는 차감량과 조용히
        // 어긋나고, 그 행이 확정 표식이라 되돌릴 근거까지 흐려진다.
        holidays(Set.of());
        setTotalLeave(15.0);
        long courseId = saveCourse(TWO_DAY_COURSE);
        String deducted = answerVisited(courseId)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long usageId = ((Number) JsonPath.read(deducted, "$.data.usages[0].id")).longValue();

        mockMvc.perform(patch(LEAVES + "/usages/{id}", usageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\": 0.5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEAVE-014"));

        // 차감량은 손대지지 않는다.
        mockMvc.perform(get(LEAVES))
                .andExpect(jsonPath("$.data.usedDays").value(2.0));
    }
}
