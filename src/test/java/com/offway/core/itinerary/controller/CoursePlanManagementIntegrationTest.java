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

    private ResultActions deduct(String guest, long courseId) throws Exception {
        return mockMvc.perform(post(COURSES + "/{id}/leave-deduction", courseId)
                .header("X-Guest-Id", guest).contentType(MediaType.APPLICATION_JSON).content("{}"));
    }

    private ResultActions list(String guest, String scope) throws Exception {
        return mockMvc.perform(get(COURSES).header("X-Guest-Id", guest).param("scope", scope));
    }

    @Test
    void 차감을_취소하면_연차가_복구된다() throws Exception {
        noHolidays();
        String guest = uniqueGuest();
        setTotalLeave(guest, 15.0);
        long courseId = saveCourse(guest, weekdayRun(10, 1));
        deduct(guest, courseId).andExpect(status().isOk()).andExpect(jsonPath("$.data.usedDays").value(1.0));

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
        long courseId = saveCourse(guest, weekdayRun(10, 1));
        deduct(guest, courseId).andExpect(status().isOk());

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
        long courseId = saveCourse(guest, weekdayRun(10, 1));
        deduct(guest, courseId).andExpect(status().isOk()).andExpect(jsonPath("$.data.remainingDays").value(14.0));

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
        LocalDate when = weekdayRun(10, 1);
        long first = saveCourse(guest, when);
        deduct(guest, first).andExpect(status().isOk());
        mockMvc.perform(delete(COURSES + "/{id}", first).header("X-Guest-Id", guest)).andExpect(status().isOk());

        long second = saveCourse(guest, when);
        deduct(guest, second)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedDays").value(1.0))
                .andExpect(jsonPath("$.data.remainingDays").value(14.0));
    }

    @Test
    void 목록이_여행_날짜와_D_day와_차감여부를_준다() throws Exception {
        noHolidays();
        String guest = uniqueGuest();
        setTotalLeave(guest, 15.0);
        LocalDate when = today().plusDays(7);
        long courseId = saveCourse(guest, when);
        deduct(guest, courseId).andExpect(status().isOk());

        list(guest, "ALL")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].travelDate").value(when.toString()))
                .andExpect(jsonPath("$.data[0].dDay").value(7))
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
}
