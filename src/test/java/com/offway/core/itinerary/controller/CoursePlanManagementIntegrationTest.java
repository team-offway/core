package com.offway.core.itinerary.controller;

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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 계획 관리(#113) 통합 테스트 — 연차↔코스 정합성과 목록 강화.
 *
 * <p>D-day·다가오는 여행 판정이 <b>오늘</b>에 의존하므로 날짜를 고정하지 않고 오늘 기준 상대 날짜로 만든다. 고정
 * 날짜를 쓰면 그 날이 지나는 순간 테스트가 깨진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class CoursePlanManagementIntegrationTest {

    private static final String COURSES = "/api/v1/courses";
    private static final String LEAVES = "/api/v1/leaves/me";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

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

    private static String uniqueGuest() {
        return "guest-" + UUID.randomUUID();
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

    /**
     * 앞으로 가장 가까운 <b>토·일 쌍</b> — 구간에 평일이 하나도 없어 차감이 0 이 되는 1박2일.
     *
     * <p>{@link #weekdayRun} 의 반대다. 상대 날짜로만 잡으면 실행 요일에 따라 평일이 섞여 들어가
     * 차감이 0 이 아니게 되고, 이 시나리오가 조용히 다른 것을 검증하게 된다.
     */
    private static LocalDate weekendRun() {
        LocalDate start = today().plusDays(1);
        while (start.getDayOfWeek() != DayOfWeek.SATURDAY) {
            start = start.plusDays(1);
        }
        return start;
    }

    /**
     * <b>지난</b> 토·일 쌍 — 차감이 걸린 주말 시나리오가 쓴다.
     *
     * <p>차감은 여행이 끝나야 되므로(#288) 앞으로 가는 {@link #weekendRun} 을 쓸 수 없다. 종료일이 오늘이면
     * 아직 여행 중일 수 있어 답할 수 없으니, 넉넉히 지난 주말을 고른다.
     */
    private static LocalDate pastWeekendRun() {
        LocalDate start = today().minusDays(7);
        while (start.getDayOfWeek() != DayOfWeek.SATURDAY) {
            start = start.minusDays(1);
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
        holidays(Set.of());
    }

    /** 공휴일을 정하고 캐시를 비운다 — 앞 테스트가 캐시에 남긴 공휴일이 이 시나리오로 새지 않게. */
    private void holidays(Set<LocalDate> days) {
        holidayClient.respond((year, month) -> days);
        leaveService.evictCache();
    }

    /** 이틀 연속 일정이 있는 코스 — 하루짜리는 차감이 늘 1.0 이라 재계산이 달라졌는지 보이지 않는다. */
    /** 첫날을 반차로 시작하는 2일 코스 — 단위는 저장 시점에 정해진다(#284·#288). */
    private static String halfDayCourseBody(LocalDate travelDate) {
        return twoDayCourseBody(travelDate).replace("\"days\":", "\"startDayLeave\": \"HALF_DAY\", \"days\":");
    }

    private static String twoDayCourseBody(LocalDate travelDate) {
        return """
                { "regionId": 16, "density": "PACKED", "transport": "CAR", "travelDate": "%s", "days": [
                  { "day": 1, "items": [
                    {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c1","title":"장소1","lat":37.50,"lng":128.60,"travelMinutes":0}
                  ]},
                  { "day": 2, "items": [
                    {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c2","title":"장소2","lat":37.51,"lng":128.61,"travelMinutes":0}
                  ]}
                ]}""".formatted(travelDate);
    }

    private long saveTwoDayCourse(String guest, LocalDate travelDate) throws Exception {
        return saveWithBody(guest, twoDayCourseBody(travelDate));
    }

    /** 본문을 그대로 받아 저장한다 — 첫날 단위처럼 템플릿에 없는 값을 실을 때 쓴다. */
    private long saveWithBody(String guest, String body) throws Exception {
        String saved = mockMvc.perform(post(COURSES).header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(saved, "$.data.courseId")).longValue();
    }

    /** 하루짜리 코스 — 여행 날짜만 바꿔 가며 쓴다. null 이면 날짜 없이 저장한다. */
    private static String courseBody(LocalDate travelDate) {
        String date = travelDate == null ? "" : "\"travelDate\": \"" + travelDate + "\",";
        return """
                { "regionId": 16, "density": "PACKED", "transport": "CAR", %s "days": [
                  { "day": 1, "items": [
                    {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c1","title":"장소1","lat":37.50,"lng":128.60,"travelMinutes":0}
                  ]}
                ]}""".formatted(date);
    }

    private long saveCourse(String guest, LocalDate travelDate) throws Exception {
        String saved = mockMvc.perform(post(COURSES).header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON).content(courseBody(travelDate)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(saved, "$.data.courseId")).longValue();
    }

    private void setTotalLeave(String guest, double totalDays) throws Exception {
        mockMvc.perform(patch(LEAVES).header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalDays\": " + totalDays + "}"))
                .andExpect(status().isOk());
    }

    /**
     * 여행을 다녀왔다고 답한다 — <b>차감의 유일한 입구</b>(#288).
     *
     * <p>여행이 <b>끝난 뒤</b>에만 답할 수 있다({@code tripNotEnded}). 그래서 차감이 걸린 시나리오는
     * 여행일을 과거로 잡는다 — 예전 {@code POST /leave-deduction} 은 시점 가드가 없어 미래 날짜로도 깎였다.
     */
    private ResultActions answerVisited(String guest, long courseId) throws Exception {
        return mockMvc.perform(post(COURSES + "/{id}/trip-outcome", courseId)
                .header("X-Guest-Id", guest)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\": \"VISITED\"}"));
    }

    /** 여행 날짜 수정(#170) — 누락·형식 오류도 보내야 해서 body 를 문자열 그대로 받는다. */
    private ResultActions patchCourse(String guest, long courseId, String bodyJson) throws Exception {
        return mockMvc.perform(patch(COURSES + "/{id}", courseId)
                .header("X-Guest-Id", guest).contentType(MediaType.APPLICATION_JSON).content(bodyJson));
    }

    private ResultActions moveTo(String guest, long courseId, LocalDate travelDate) throws Exception {
        return patchCourse(guest, courseId, "{\"travelDate\": \"" + travelDate + "\"}");
    }

    private ResultActions myLeave(String guest) throws Exception {
        return mockMvc.perform(get(LEAVES).header("X-Guest-Id", guest));
    }

    private ResultActions list(String guest, String scope) throws Exception {
        return mockMvc.perform(get(COURSES).header("X-Guest-Id", guest).param("scope", scope));
    }

    private ResultActions list(String guest, String scope, String page, String size) throws Exception {
        return mockMvc.perform(get(COURSES).header("X-Guest-Id", guest)
                .param("scope", scope).param("page", page).param("size", size));
    }

    /** 페이지 경계를 보려면 코스가 여럿이어야 한다 — 전부 같은 날짜라 저장 순서(최근이 위)로 정렬된다. */
    private void saveCourses(String guest, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            saveCourse(guest, LocalDate.now(KST).plusDays(10));
        }
    }

    @Test
    void 목록은_페이지_정보를_함께_준다() throws Exception {
        String guest = uniqueGuest();
        saveCourses(guest, 3);

        list(guest, "ALL")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.pageResponse.page").value(0))
                .andExpect(jsonPath("$.pageResponse.size").value(20))
                .andExpect(jsonPath("$.pageResponse.totalElements").value(3))
                .andExpect(jsonPath("$.pageResponse.totalPages").value(1));
    }

    @Test
    void 페이지를_넘기면_나머지가_나온다() throws Exception {
        String guest = uniqueGuest();
        saveCourses(guest, 3);

        list(guest, "ALL", "0", "2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.pageResponse.totalElements").value(3))
                .andExpect(jsonPath("$.pageResponse.totalPages").value(2));

        list(guest, "ALL", "1", "2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.pageResponse.page").value(1));
    }

    @Test
    void 상한을_넘는_size는_잘린다() throws Exception {
        // 상한이 없으면 이 요청 한 번으로 페이지네이션이 없던 때와 같아진다(#105).
        // 거절하지 않고 자르는 쪽을 택했다 — 목록이 통째로 비는 것보다 낫다.
        String guest = uniqueGuest();
        saveCourses(guest, 1);

        list(guest, "ALL", "0", "100000")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageResponse.size").value(100));
    }

    @Test
    void 음수_페이지는_첫_페이지로_본다() throws Exception {
        String guest = uniqueGuest();
        saveCourses(guest, 1);

        list(guest, "ALL", "-5", "20")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.pageResponse.page").value(0));
    }

    @Test
    void 범위를_벗어난_페이지는_빈_목록이다() throws Exception {
        String guest = uniqueGuest();
        saveCourses(guest, 1);

        list(guest, "ALL", "9", "20")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.pageResponse.totalElements").value(1));
    }

    @Test
    void 차감을_취소하면_연차가_복구된다() throws Exception {
        noHolidays();
        String guest = uniqueGuest();
        setTotalLeave(guest, 15.0);
        long courseId = saveCourse(guest, weekdayRun(-10, 1));
        answerVisited(guest, courseId).andExpect(status().isOk()).andExpect(jsonPath("$.data.usedDays").value(1.0));

        mockMvc.perform(delete(COURSES + "/{id}/leave-deduction", courseId).header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.usedDays").value(0.0))
                .andExpect(jsonPath("$.data.remainingDays").value(15.0))
                .andExpect(jsonPath("$.data.usages.length()").value(0));
    }

    @Test
    void 차감된_적_없는_코스의_취소도_200이다() throws Exception {
        // 멱등 — 사용자가 원한 상태(차감 없음)는 이미 이뤄져 있다. 404 를 주면 화면이 "취소 실패" 를 보여준다.
        noHolidays();
        String guest = uniqueGuest();
        setTotalLeave(guest, 15.0);
        long courseId = saveCourse(guest, today().plusDays(10));

        mockMvc.perform(delete(COURSES + "/{id}/leave-deduction", courseId).header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remainingDays").value(15.0));
    }

    @Test
    void 취소를_두_번_해도_연차가_더_늘지_않는다() throws Exception {
        noHolidays();
        String guest = uniqueGuest();
        setTotalLeave(guest, 15.0);
        long courseId = saveCourse(guest, weekdayRun(-10, 1));
        answerVisited(guest, courseId).andExpect(status().isOk());

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(delete(COURSES + "/{id}/leave-deduction", courseId).header("X-Guest-Id", guest))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.remainingDays").value(15.0));
        }
    }

    @Test
    void 남의_코스의_차감은_취소할_수_없다() throws Exception {
        noHolidays();
        String owner = uniqueGuest();
        long courseId = saveCourse(owner, today().plusDays(10));

        mockMvc.perform(delete(COURSES + "/{id}/leave-deduction", courseId).header("X-Guest-Id", uniqueGuest()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-003"));
    }

    @Test
    void 코스를_지우면_차감한_연차도_함께_돌아온다() throws Exception {
        // 나뉘어 있으면 "코스는 사라졌는데 연차는 깎인 채" 가 남고, 코스가 없어 취소 API 로도 못 고친다.
        noHolidays();
        String guest = uniqueGuest();
        setTotalLeave(guest, 15.0);
        long courseId = saveCourse(guest, weekdayRun(-10, 1));
        answerVisited(guest, courseId).andExpect(status().isOk()).andExpect(jsonPath("$.data.remainingDays").value(14.0));

        mockMvc.perform(delete(COURSES + "/{id}", courseId).header("X-Guest-Id", guest))
                .andExpect(status().isOk());

        mockMvc.perform(get(LEAVES).header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remainingDays").value(15.0))
                .andExpect(jsonPath("$.data.usages.length()").value(0));
    }

    @Test
    void 지운_코스와_같은_내용을_다시_저장하면_다시_차감할_수_있다() throws Exception {
        // 삭제가 내역까지 지웠는지 확인하는 다른 각도 — 내역이 남아 있으면 새 코스 차감이 막히거나 잔액이 어긋난다.
        noHolidays();
        String guest = uniqueGuest();
        setTotalLeave(guest, 15.0);
        LocalDate when = weekdayRun(-10, 1);
        long first = saveCourse(guest, when);
        answerVisited(guest, first).andExpect(status().isOk());
        mockMvc.perform(delete(COURSES + "/{id}", first).header("X-Guest-Id", guest)).andExpect(status().isOk());

        long second = saveCourse(guest, when);
        answerVisited(guest, second)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedDays").value(1.0))
                .andExpect(jsonPath("$.data.remainingDays").value(14.0));
    }

    @Test
    void 목록이_여행_날짜와_D_day와_차감여부를_준다() throws Exception {
        noHolidays();
        String guest = uniqueGuest();
        setTotalLeave(guest, 15.0);
        // 차감을 부르므로 여행일이 평일이어야 한다 — plusDays(7) 은 7의 배수라 요일이 그대로 보존돼,
        // 토·일에 돌리면 차감할 평일이 0일이 되고 "0 은 차감하지 않는다"(LeaveDays) 규칙에 걸려 400 이 난다.
        LocalDate when = weekdayRun(-7, 1);
        long courseId = saveCourse(guest, when);
        answerVisited(guest, courseId).andExpect(status().isOk());

        list(guest, "ALL")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].travelDate").value(when.toString()))
                // 평일로 밀린 만큼 D-day 도 달라진다 — 상수 7 로 두면 밀린 날에 깨진다.
                .andExpect(jsonPath("$.data[0].dDay").value((int) ChronoUnit.DAYS.between(today(), when)))
                .andExpect(jsonPath("$.data[0].leaveDeducted").value(true))
                .andExpect(jsonPath("$.data[0].placeCount").value(1));
    }

    @Test
    void 오늘_출발이면_D_day는_0이고_다가오는_여행이다() throws Exception {
        noHolidays();
        String guest = uniqueGuest();
        long courseId = saveCourse(guest, today());

        list(guest, "UPCOMING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].courseId").value(courseId))
                .andExpect(jsonPath("$.data[0].dDay").value(0));
    }

    @Test
    void 지난_여행은_D_day가_음수이고_PAST로만_나온다() throws Exception {
        noHolidays();
        String guest = uniqueGuest();
        long past = saveCourse(guest, today().minusDays(3));

        list(guest, "PAST")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].courseId").value(past))
                .andExpect(jsonPath("$.data[0].dDay").value(-3));
        list(guest, "UPCOMING").andExpect(jsonPath("$.data.length()").value(0));
    }

    /**
     * <b>여행 중인 코스는 아직 "다녀온 여행" 이 아니다</b>(#325).
     *
     * <p>예전에는 시작일로 갈라서, 2박3일 여행 둘째 날에 그 코스가 이미 PAST 로 넘어갔다. 앱의 칩은
     * 종료일 기준이라 아직 D-DAY 였고, "다녀오셨나요?" 모달도 종료일 다음 날에 뜬다 — 결국 다녀온 여행
     * 탭 안에 D-DAY 코스가 이틀간 앉아 있었다.
     */
    @Test
    void 여행_중인_코스는_UPCOMING_에_남는다() throws Exception {
        noHolidays();
        String guest = uniqueGuest();
        // 어제 출발한 1박2일 — 오늘이 마지막 날이라 아직 끝나지 않았다.
        long ongoing = saveTwoDayCourse(guest, today().minusDays(1));

        list(guest, "UPCOMING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].courseId").value(ongoing));
        list(guest, "PAST").andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 종료일이_지나야_PAST_로_넘어간다() throws Exception {
        // 경계를 못박는다 — 어제 끝난 여행은 PAST, 오늘 끝나는 여행은 UPCOMING 이다.
        noHolidays();
        String guest = uniqueGuest();
        long ended = saveTwoDayCourse(guest, today().minusDays(2)); // 그저께~어제

        list(guest, "PAST")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].courseId").value(ended));
        list(guest, "UPCOMING").andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 다가오는_여행은_가까운_것부터_준다() throws Exception {
        noHolidays();
        String guest = uniqueGuest();
        // 일부러 먼 것부터 저장한다 — 저장 순서가 아니라 여행 날짜로 정렬되는지 봐야 한다.
        long far = saveCourse(guest, today().plusDays(30));
        long near = saveCourse(guest, today().plusDays(2));

        list(guest, "UPCOMING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].courseId").value(near))
                .andExpect(jsonPath("$.data[1].courseId").value(far));
    }

    @Test
    void 여행_날짜가_없는_코스는_ALL에만_나온다() throws Exception {
        // 날짜가 없으면 다가오는 여행인지 지난 여행인지 판단할 근거가 없다. 아무 쪽에나 넣으면 화면이 거짓말을 한다.
        noHolidays();
        String guest = uniqueGuest();
        long undated = saveCourse(guest, null);

        list(guest, "ALL")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].courseId").value(undated))
                .andExpect(jsonPath("$.data[0].travelDate").doesNotExist())
                .andExpect(jsonPath("$.data[0].dDay").doesNotExist());
        list(guest, "UPCOMING").andExpect(jsonPath("$.data.length()").value(0));
        list(guest, "PAST").andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void scope가_이상하면_400이다() throws Exception {
        list(uniqueGuest(), "TOMORROW").andExpect(status().isBadRequest());
    }

    // ── 여행 날짜 수정(#170) ─────────────────────────────────────────────

    @Test
    void 여행_날짜를_옮기면_새_날짜_기준으로_다시_그릴_수_있게_준다() throws Exception {
        noHolidays();
        String guest = uniqueGuest();
        LocalDate moved = weekdayRun(30, 1);
        long courseId = saveCourse(guest, today().plusDays(10));

        moveTo(guest, courseId, moved)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.travelDate").value(moved.toString()))
                // 날짜만 바꾼 게 아니라 파생값도 따라와야 한다 — 화면이 "day 1  9.9/수" 를 그리는 재료다.
                .andExpect(jsonPath("$.data.days[0].date").value(moved.toString()))
                .andExpect(jsonPath("$.data.days[0].dayOfWeek").value(moved.getDayOfWeek().name()));
    }

    @Test
    void 옮긴_날짜가_다음_조회에도_남는다() throws Exception {
        // 응답만 새 날짜고 DB 는 옛 날짜면, 화면을 새로고침하는 순간 날짜가 되돌아간다.
        noHolidays();
        String guest = uniqueGuest();
        LocalDate moved = weekdayRun(30, 1);
        long courseId = saveCourse(guest, today().plusDays(10));

        moveTo(guest, courseId, moved).andExpect(status().isOk());

        mockMvc.perform(get(COURSES + "/{id}", courseId).header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.travelDate").value(moved.toString()));
    }

    @Test
    void 상세_응답이_이동수단을_준다() throws Exception {
        // 이 필드가 없어 "지우고 다시 저장" 우회조차 막혀 있었다 — POST /courses 는 transport 가 필수다.
        noHolidays();
        String guest = uniqueGuest();
        long courseId = saveCourse(guest, today().plusDays(10));

        mockMvc.perform(get(COURSES + "/{id}", courseId).header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transport").value("CAR"));
    }

    @Test
    void 날짜를_옮기면_차감량을_다시_계산한다() throws Exception {
        // 서버가 정말 다시 계산한다는 증거 — 같은 코스인데 옮겨간 구간에 공휴일이 하나 껴서 차감이 줄어든다.
        noHolidays();
        String guest = uniqueGuest();
        setTotalLeave(guest, 15.0);
        long courseId = saveTwoDayCourse(guest, weekdayRun(-10, 2));
        answerVisited(guest, courseId).andExpect(status().isOk()).andExpect(jsonPath("$.data.usedDays").value(2.0));

        LocalDate moved = weekdayRun(40, 2);
        holidays(Set.of(moved.plusDays(1)));
        moveTo(guest, courseId, moved).andExpect(status().isOk());

        myLeave(guest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedDays").value(1.0))
                .andExpect(jsonPath("$.data.remainingDays").value(14.0))
                // 내역을 새로 쌓지 않고 그 행을 옮긴다 — 코스당 한 행이 규칙이다(#91).
                .andExpect(jsonPath("$.data.usages.length()").value(1))
                .andExpect(jsonPath("$.data.usages[0].usedOn").value(moved.toString()));
    }

    @Test
    void 반차로_확정한_코스는_옮겨도_반차가_유지된다() throws Exception {
        // 반차 여부는 확정할 때 사용자가 고른 값이라 날짜를 고쳤다고 바뀌지 않는다. 어디에도 안 남기면
        // 재계산이 종일로 되돌아가 0.5 를 더 깎는다 — 화면에는 아무 표시도 안 난다.
        noHolidays();
        String guest = uniqueGuest();
        setTotalLeave(guest, 15.0);
        // 차감은 여행이 끝나야 되므로 출발은 과거로, 옮기는 곳은 미래로 잡는다(#288).
        // 두 가드가 반대 방향이다 — 지난 날짜로는 옮길 수 없다(ITINERARY-007).
        long courseId = saveWithBody(guest, halfDayCourseBody(weekdayRun(-20, 2)));
        answerVisited(guest, courseId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedDays").value(1.5));

        moveTo(guest, courseId, weekdayRun(40, 2)).andExpect(status().isOk());

        myLeave(guest).andExpect(jsonPath("$.data.usedDays").value(1.5));
    }

    @Test
    void 차감하지_않은_코스는_날짜만_바뀌고_연차는_그대로다() throws Exception {
        noHolidays();
        String guest = uniqueGuest();
        setTotalLeave(guest, 15.0);
        long courseId = saveTwoDayCourse(guest, weekdayRun(-10, 2));

        moveTo(guest, courseId, weekdayRun(40, 2)).andExpect(status().isOk());

        myLeave(guest)
                .andExpect(jsonPath("$.data.remainingDays").value(15.0))
                .andExpect(jsonPath("$.data.usages.length()").value(0));
    }

    @Test
    void 깎을_평일이_없는_구간으로_옮기면_차감이_0이_된다() throws Exception {
        // 옛 값을 남겨두면 가지도 않을 평일만큼 깎인 채로 굳는다. 그렇다고 행을 지우면 날짜를 고쳤다는
        // 이유로 확정이 풀린다(#212) — 0 으로 남겨 "확정했고 깎을 것이 없다" 를 표현한다.
        noHolidays();
        String guest = uniqueGuest();
        setTotalLeave(guest, 15.0);
        long courseId = saveTwoDayCourse(guest, weekdayRun(-10, 2));
        answerVisited(guest, courseId).andExpect(status().isOk()).andExpect(jsonPath("$.data.usedDays").value(2.0));

        LocalDate moved = weekdayRun(40, 2);
        holidays(Set.of(moved, moved.plusDays(1)));
        moveTo(guest, courseId, moved).andExpect(status().isOk());

        myLeave(guest)
                .andExpect(jsonPath("$.data.remainingDays").value(15.0))
                .andExpect(jsonPath("$.data.usages.length()").value(1))
                .andExpect(jsonPath("$.data.usages[0].days").value(0.0));
        // 확정은 유지된다 — 목록의 차감 표시가 꺼지면 사용자는 다시 눌러야 하는 줄 안다.
        list(guest, "UPCOMING").andExpect(jsonPath("$.data[?(@.courseId == " + courseId + ")].leaveDeducted")
                .value(org.hamcrest.Matchers.hasItem(true)));
    }

    @Test
    void 주말뿐인_코스도_확정할_수_있다() throws Exception {
        // 깎을 평일이 없으면 400 이 났다(LEAVE-010). 사용자는 정상적인 주말 여행을 확정한 것뿐인데
        // "연차 사용 일수가 올바르지 않습니다" 를 봤다(#212).
        noHolidays();
        String guest = uniqueGuest();
        setTotalLeave(guest, 15.0);
        long courseId = saveTwoDayCourse(guest, pastWeekendRun());

        answerVisited(guest, courseId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.usedDays").value(0.0))
                .andExpect(jsonPath("$.data.remainingDays").value(15.0))
                // 내역은 남는다 — 차감량이자 확정 표식이다.
                .andExpect(jsonPath("$.data.usages.length()").value(1))
                .andExpect(jsonPath("$.data.usages[0].days").value(0.0));
    }

    @Test
    void 주말뿐인_여행에_두_번_답해도_내역이_늘지_않는다() throws Exception {
        // 0 짜리 행도 멱등해야 한다 — 유니크 제약(#91)이 그대로 막는지 본다.
        noHolidays();
        String guest = uniqueGuest();
        setTotalLeave(guest, 15.0);
        long courseId = saveTwoDayCourse(guest, pastWeekendRun());

        answerVisited(guest, courseId).andExpect(status().isOk());
        // 두 번째 답은 409 다(#288) — 예전 POST /leave-deduction 은 "차감 명령" 이라 멱등하게 200 이었지만,
        // 이제는 "다녀왔다는 답" 이라 두 번 답할 수 없다. 0 짜리 내역이어도 규칙은 같다.
        answerVisited(guest, courseId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITINERARY-005"));
    }

    @Test
    void 지난_날짜로는_옮길_수_없다() throws Exception {
        // 옮기는 순간 "끝난 여행" 이 돼 홈이 "다녀오셨나요?" 를 묻는다(#116). 사용자는 계획을 고쳤을 뿐이다.
        noHolidays();
        String guest = uniqueGuest();
        long courseId = saveCourse(guest, today().plusDays(10));

        moveTo(guest, courseId, today().minusDays(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("ITINERARY-007"));
    }

    @Test
    void 날짜를_놓친_코스는_앞으로_당겨올_수_있다() throws Exception {
        // 판단 대상은 옮겨갈 날짜다. 지금 날짜가 지났다고 막으면 이 기능이 가장 필요한 경우를 막는 셈이다.
        noHolidays();
        String guest = uniqueGuest();
        LocalDate moved = weekdayRun(30, 1);
        long courseId = saveCourse(guest, today().minusDays(5));

        moveTo(guest, courseId, moved)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.travelDate").value(moved.toString()));
    }

    @Test
    void 오늘로는_옮길_수_있다() throws Exception {
        // 경계 — 오늘은 아직 지나지 않았다. 오늘 출발이 다가오는 여행인 것과 같은 기준이다.
        noHolidays();
        String guest = uniqueGuest();
        long courseId = saveCourse(guest, today().plusDays(10));

        moveTo(guest, courseId, today())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.travelDate").value(today().toString()));
    }

    @Test
    void 날짜를_안_보내면_400이다() throws Exception {
        // 빈 body 를 200 으로 받아주면 아무것도 안 바뀐 응답이 성공처럼 보인다.
        noHolidays();
        String guest = uniqueGuest();
        long courseId = saveCourse(guest, today().plusDays(10));

        patchCourse(guest, courseId, "{}").andExpect(status().isBadRequest());
    }

    @Test
    void 남의_코스는_옮길_수_없다() throws Exception {
        // 조회·삭제와 같은 규칙 — 존재 여부를 흘리지 않도록 404 다.
        noHolidays();
        long courseId = saveCourse(uniqueGuest(), today().plusDays(10));

        moveTo(uniqueGuest(), courseId, today().plusDays(20))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-003"));
    }
}
