package com.offway.core.itinerary.controller;

import static com.offway.core.user.config.TestLogins.loginAs;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.testSecurityContext;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
 * 홈 진입 모달 "다녀오셨나요?"(#116) 통합 테스트.
 *
 * <p>"여행이 끝났는가" 판정이 <b>오늘</b>에 의존하므로 날짜를 고정하지 않고 오늘 기준 상대 날짜로 만든다. 고정
 * 날짜를 쓰면 그 날이 지나는 순간 테스트가 깨진다.
 *
 * <p>DB 격리: 값 없는 {@link WithLoginUser} 가 <b>테스트마다 새 사용자</b>를 넣는다 — 밀린 여행 목록도 연차
 * 잔액도 사용자 단위라 이전 실행·다른 테스트와 섞이지 않는다(#280 이전의 고유 게스트 ID 와 같은 역할).
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithLoginUser
class TripOutcomeIntegrationTest {

    private static final String COURSES = "/api/v1/courses";
    private static final String PENDING = COURSES + "/pending-trips";
    private static final String LEAVES = "/api/v1/leaves/me";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** {@code SecurityConfig} · {@code JwtAuthenticationFilter} 가 쓰는 권한 이름 — 같은 값이어야 한다. */
    private static final String USER_AUTHORITY = "ROLE_USER";

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


    private static LocalDate today() {
        return LocalDate.now(KST);
    }

    /**
     * 오늘에서 {@code daysFromToday} 만큼 떨어진 지점부터, <b>구간 전체가 평일인</b> 가장 가까운 시작일.
     *
     * <p>상대 날짜만으로는 요일이 고정되지 않아 차감 일수를 단정하는 테스트가 실행 요일에 따라 깨진다. 시작일만
     * 평일로 맞춰서도 부족하다 — 1박2일이 금요일에 시작하면 둘째 날이 토요일이라 차감이 1일이 된다.
     *
     * <p>과거를 물으면 과거 쪽으로 민다. 앞으로 밀면 "이미 지난 여행" 이라는 전제가 깨진다.
     */
    private static LocalDate weekdayRun(int daysFromToday, int length) {
        int step = daysFromToday < 0 ? -1 : 1;
        LocalDate start = today().plusDays(daysFromToday);
        while (!allWeekdays(start, length)) {
            start = start.plusDays(step);
        }
        return start;
    }

    private static boolean allWeekdays(LocalDate start, int length) {
        for (int i = 0; i < length; i++) {
            DayOfWeek day = start.plusDays(i).getDayOfWeek();
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                return false;
            }
        }
        return true;
    }

    private void noHolidays() {
        holidayClient.respond((year, month) -> Set.of());
        leaveService.evictCache();
    }

    /** 1박2일 코스. {@code travelDate} 가 null 이면 날짜 없이 저장한다. */
    private static String twoDayCourse(LocalDate travelDate) {
        String date = travelDate == null ? "" : "\"travelDate\": \"" + travelDate + "\",";
        return """
                { "regionId": 16, "density": "PACKED", "transport": "CAR", %s "days": [
                  { "day": 1, "items": [
                    {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c1","title":"장소1","lat":37.38,"lng":128.66,"travelMinutes":0}
                  ]},
                  { "day": 2, "items": [
                    {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c2","title":"장소2","lat":37.39,"lng":128.67,"travelMinutes":0}
                  ]}
                ]}""".formatted(date);
    }

    private long saveCourse(LocalDate travelDate) throws Exception {
        return saveCourse(travelDate, testSecurityContext());
    }

    private long saveCourse(LocalDate travelDate, RequestPostProcessor as) throws Exception {
        String saved = mockMvc.perform(post(COURSES).with(as)
                        .contentType(MediaType.APPLICATION_JSON).content(twoDayCourse(travelDate)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(saved, "$.data.courseId")).longValue();
    }

    private void setTotalLeave(double totalDays) throws Exception {
        mockMvc.perform(patch(LEAVES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalDays\": " + totalDays + "}"))
                .andExpect(status().isOk());
    }

    private ResultActions pending() throws Exception {
        return mockMvc.perform(get(PENDING));
    }

    private ResultActions answer(long courseId, String outcome) throws Exception {
        return mockMvc.perform(post(COURSES + "/{id}/trip-outcome", courseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\": \"" + outcome + "\"}"));
    }

    @Test
    void 여행이_끝난_다음_날부터_물어본다() throws Exception {
        noHolidays();
        setTotalLeave(13.0);
        // 그저께 시작한 1박2일 → 어제 끝났다
        long courseId = saveCourse(weekdayRun(-2, 2));

        pending()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.remainingDays").value(13.0))
                .andExpect(jsonPath("$.data.trips.length()").value(1))
                .andExpect(jsonPath("$.data.trips[0].courseId").value(courseId))
                .andExpect(jsonPath("$.data.trips[0].travelDays").value(2))
                .andExpect(jsonPath("$.data.trips[0].consumedLeaveDays").value(2.0));
    }

    @Test
    void 오늘_끝나는_여행은_아직_묻지_않는다() throws Exception {
        // 아직 여행 중일 수 있다. 하루가 지나야 대상이다.
        noHolidays();
        saveCourse(today().minusDays(1)); // 어제 시작 1박2일 → 오늘 끝난다

        pending()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trips.length()").value(0));
    }

    @Test
    void 아직_안_간_여행은_묻지_않는다() throws Exception {
        noHolidays();
        saveCourse(today().plusDays(10));

        pending().andExpect(jsonPath("$.data.trips.length()").value(0));
    }

    @Test
    void 다녀왔다고_답하면_연차가_깎이고_다시_묻지_않는다() throws Exception {
        noHolidays();
        setTotalLeave(13.0);
        long courseId = saveCourse(weekdayRun(-3, 2));

        answer(courseId, "VISITED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedDays").value(2.0))
                .andExpect(jsonPath("$.data.remainingDays").value(11.0))
                .andExpect(jsonPath("$.data.usages.length()").value(1));

        pending()
                .andExpect(jsonPath("$.data.trips.length()").value(0))
                .andExpect(jsonPath("$.data.remainingDays").value(11.0));
    }

    @Test
    void 안_갔다고_답하면_연차는_그대로이고_다시_묻지_않는다() throws Exception {
        // 안 간 것도 기록해야 한다 — 기록하지 않으면 "아직 안 물어본 것" 과 구분되지 않아 매번 다시 뜬다.
        noHolidays();
        setTotalLeave(13.0);
        long courseId = saveCourse(today().minusDays(3));

        answer(courseId, "NOT_VISITED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedDays").value(0.0))
                .andExpect(jsonPath("$.data.remainingDays").value(13.0))
                .andExpect(jsonPath("$.data.usages.length()").value(0));

        pending().andExpect(jsonPath("$.data.trips.length()").value(0));
    }

    @Test
    void 이미_답한_여행에_다시_답하면_409다() throws Exception {
        noHolidays();
        setTotalLeave(13.0);
        long courseId = saveCourse(weekdayRun(-3, 2));
        answer(courseId, "VISITED").andExpect(status().isOk());

        answer(courseId, "NOT_VISITED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("ITINERARY-005"));

        // 첫 답이 그대로 남아 연차가 두 번 움직이지 않는다
        mockMvc.perform(get(LEAVES))
                .andExpect(jsonPath("$.data.remainingDays").value(11.0));
    }

    @Test
    void 다녀왔다고_답한_여행은_다시_묻지_않는다() throws Exception {
        // 예전에는 "내 코스에서 차감을 눌렀으면 묻지 않는다" 였다. 차감 입구가 trip-outcome 하나가 되면서
        // (#288) 차감은 답 없이 존재할 수 없고, 그래서 "차감했다" 와 "답했다" 가 같은 사실이 됐다.
        noHolidays();
        setTotalLeave(13.0);
        // 여행일이 평일이어야 한다 — 주말이면 차감할 평일이 0일이라 "0 은 차감하지 않는다"(LeaveDays)에 걸린다.
        long courseId = saveCourse(weekdayRun(-3, 1));
        answer(courseId, "VISITED").andExpect(status().isOk());

        pending().andExpect(jsonPath("$.data.trips.length()").value(0));
    }

    @Test
    void 여행_날짜가_없는_코스는_묻지_않는다() throws Exception {
        // 지났는지 판단할 근거가 없다.
        noHolidays();
        saveCourse(null);

        pending().andExpect(jsonPath("$.data.trips.length()").value(0));
    }

    @Test
    void 남의_코스에는_답할_수_없다() throws Exception {
        noHolidays();
        long courseId = saveCourse(today().minusDays(3), loginAs(UUID.randomUUID()));

        answer(courseId, "VISITED")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-003"));
    }

    @Test
    void 남의_지난_여행은_묻지_않는다() throws Exception {
        // 모달은 내 여행만 물어야 한다 — 남의 여행이 뜨면 남의 일정이 그대로 노출되고,
        // 답하는 순간 내 연차가 남의 여행으로 깎인다.
        noHolidays();
        setTotalLeave(13.0);
        saveCourse(weekdayRun(-3, 2), loginAs(UUID.randomUUID()));

        pending()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trips.length()").value(0));
    }

    @Test
    void outcome이_잘못되면_400이다() throws Exception {
        noHolidays();
        long courseId = saveCourse(today().minusDays(3));

        answer(courseId, "MAYBE").andExpect(status().isBadRequest());
    }

    @Test
    void 모달이_카드를_그릴_재료를_한_번에_받는다() throws Exception {
        // 지역명·차감될 연차·지도 좌표까지 — 코스 상세를 다시 부르지 않아도 되게.
        noHolidays();
        setTotalLeave(13.0);
        LocalDate start = today().minusDays(4);
        saveCourse(start);

        pending()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trips[0].regionName").isNotEmpty())
                .andExpect(jsonPath("$.data.trips[0].travelDate").value(start.toString()))
                .andExpect(jsonPath("$.data.trips[0].travelEndDate").value(start.plusDays(1).toString()))
                .andExpect(jsonPath("$.data.trips[0].placeCount").value(2))
                .andExpect(jsonPath("$.data.trips[0].points.length()").value(2))
                .andExpect(jsonPath("$.data.trips[0].points[0].day").value(1))
                .andExpect(jsonPath("$.data.trips[0].points[0].lat").value(37.38))
                .andExpect(jsonPath("$.data.trips[0].points[1].day").value(2));
    }

    @Test
    void 밀린_여행이_여러_건이면_모두_준다() throws Exception {
        // 모달은 한 번에 하나씩 보여주지만, 목록은 한 번에 받아 왕복을 줄인다.
        noHolidays();
        setTotalLeave(13.0);
        saveCourse(today().minusDays(3));
        saveCourse(today().minusDays(20));

        pending()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trips.length()").value(2));
    }

    @Test
    void 아직_끝나지_않은_여행에는_답할_수_없다() throws Exception {
        // 모달이 묻지 않은 것에 답이 들어오면 다녀오지도 않았는데 연차가 깎인다.
        noHolidays();
        setTotalLeave(13.0);
        long courseId = saveCourse(weekdayRun(10, 2));

        answer(courseId, "VISITED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITINERARY-006"));

        mockMvc.perform(get(LEAVES))
                .andExpect(jsonPath("$.data.remainingDays").value(13.0))
                .andExpect(jsonPath("$.data.usages.length()").value(0));
    }

    @Test
    void 오늘_끝나는_여행에도_답할_수_없다() throws Exception {
        // pending 이 종료 당일을 빼는 것과 같은 기준이어야 한다.
        noHolidays();
        setTotalLeave(13.0);
        long courseId = saveCourse(today().minusDays(1)); // 어제 시작 1박2일 → 오늘 끝난다

        answer(courseId, "VISITED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITINERARY-006"));
    }

    @Test
    void 다녀왔다고_답한_뒤_안_갔다고_뒤집을_수_없다() throws Exception {
        // 통과시키면 차감은 남은 채 "안 갔다" 로 기록되고, 모달에도 안 떠서 화면에서 바로잡을 길이 사라진다.
        noHolidays();
        setTotalLeave(13.0);
        long courseId = saveCourse(weekdayRun(-3, 2));
        answer(courseId, "VISITED").andExpect(status().isOk());

        answer(courseId, "NOT_VISITED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITINERARY-005"));

        // 차감이 그대로 남는다 — 되돌리려면 차감 취소 API 를 쓴다
        mockMvc.perform(get(LEAVES))
                .andExpect(jsonPath("$.data.remainingDays").value(11.0));
    }
}
