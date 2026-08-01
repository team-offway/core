package com.offway.core.itinerary.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.offway.core.leave.infrastructure.holiday.HolidayClient;
import com.offway.core.leave.infrastructure.holiday.StubHolidayClient;
import com.offway.core.leave.service.LeaveService;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 코스 확정 → 연차 차감(#91) 통합 테스트.
 *
 * <p>DB 격리는 롤백 대신 테스트마다 고유 게스트 ID 로 한다 — 연차 잔액·사용내역이 게스트 단위로 묶이므로 서로 섞이지
 * 않는다. 공휴일은 외부 호출이라 stub 으로 격리하고, 캐시가 컨텍스트를 공유하므로 각 테스트가 본문에서 비운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class CourseLeaveDeductionIntegrationTest {

    private static final String COURSES = "/api/v1/courses";
    private static final String LEAVES = "/api/v1/leaves/me";

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

    private static String uniqueGuest() {
        return "guest-" + UUID.randomUUID();
    }

    /** 공휴일 동작을 정하고 캐시를 비운다 — 앞 테스트가 캐시에 남긴 공휴일이 이 시나리오로 새지 않게. */
    private void holidays(Set<LocalDate> holidays) {
        holidayClient.respond((year, month) -> holidays);
        leaveService.evictCache();
    }

    private void setTotalLeave(String guest, double totalDays) throws Exception {
        mockMvc.perform(patch(LEAVES).header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalDays\": " + totalDays + "}"))
                .andExpect(status().isOk());
    }

    private long saveCourse(String guest, String body) throws Exception {
        String saved = mockMvc.perform(post(COURSES).header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(saved, "$.data.courseId")).longValue();
    }

    private org.springframework.test.web.servlet.ResultActions deduct(String guest, long courseId, String body)
            throws Exception {
        // 인증을 명시한다 — 동시성 테스트가 별도 스레드에서 부르는데 @WithMockUser 는 현재 스레드에만 적용된다.
        return mockMvc.perform(post(COURSES + "/{id}/leave-deduction", courseId)
                .header("X-Guest-Id", guest)
                .with(httpBasic("dev", "dev"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void 코스를_확정하면_평일_수만큼_연차가_차감된다() throws Exception {
        holidays(Set.of());
        String guest = uniqueGuest();
        setTotalLeave(guest, 15.0);
        long courseId = saveCourse(guest, TWO_DAY_COURSE);

        deduct(guest, courseId, "{}")
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
    void 구간에_공휴일이_끼면_그만큼_덜_차감된다() throws Exception {
        // 서버가 실제로 다시 계산한다는 증거 — 요청은 위 테스트와 똑같고 공휴일만 다른데 결과가 바뀐다.
        holidays(Set.of(START.plusDays(1)));
        String guest = uniqueGuest();
        setTotalLeave(guest, 15.0);
        long courseId = saveCourse(guest, TWO_DAY_COURSE);

        deduct(guest, courseId, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedDays").value(1.0))
                .andExpect(jsonPath("$.data.remainingDays").value(14.0));
    }

    @Test
    void 반차로_시작하면_0_5일_덜_차감된다() throws Exception {
        holidays(Set.of());
        String guest = uniqueGuest();
        setTotalLeave(guest, 15.0);
        long courseId = saveCourse(guest, TWO_DAY_COURSE);

        deduct(guest, courseId, "{\"halfDayStart\": true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedDays").value(1.5))
                .andExpect(jsonPath("$.data.remainingDays").value(13.5));
    }

    @Test
    void 같은_코스를_다시_확정해도_내역이_늘지_않는다() throws Exception {
        holidays(Set.of());
        String guest = uniqueGuest();
        setTotalLeave(guest, 15.0);
        long courseId = saveCourse(guest, TWO_DAY_COURSE);

        deduct(guest, courseId, "{}").andExpect(status().isOk());
        deduct(guest, courseId, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedDays").value(2.0))
                .andExpect(jsonPath("$.data.usages.length()").value(1));

        mockMvc.perform(get(LEAVES).header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remainingDays").value(13.0))
                .andExpect(jsonPath("$.data.usages.length()").value(1));
    }

    @Test
    void 남은_연차가_부족해도_차감되고_잔여가_음수가_된다() throws Exception {
        // 결정 #38 — 서버는 막지 않는다. 경고와 확인은 프론트가 맡는다.
        holidays(Set.of());
        String guest = uniqueGuest();
        setTotalLeave(guest, 1.0);
        long courseId = saveCourse(guest, TWO_DAY_COURSE);

        deduct(guest, courseId, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedDays").value(2.0))
                .andExpect(jsonPath("$.data.remainingDays").value(-1.0));
    }

    @Test
    void 여행_날짜가_없는_코스는_400이다() throws Exception {
        holidays(Set.of());
        String guest = uniqueGuest();
        long courseId = saveCourse(guest, COURSE_WITHOUT_DATE);

        deduct(guest, courseId, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("ITINERARY-004"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 남의_코스로는_차감할_수_없다() throws Exception {
        holidays(Set.of());
        String owner = uniqueGuest();
        long courseId = saveCourse(owner, TWO_DAY_COURSE);

        deduct(uniqueGuest(), courseId, "{}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-003"));
    }

    @Test
    void 같은_코스를_동시에_확정해도_내역은_하나다() throws Exception {
        // 조회로만 거르면 두 요청이 둘 다 "아직 안 했다" 를 보고 두 번 차감한다 — 유니크 제약이 막는지 확인한다.
        holidays(Set.of());
        String guest = uniqueGuest();
        setTotalLeave(guest, 15.0);
        long courseId = saveCourse(guest, TWO_DAY_COURSE);

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
                        statuses.add(deduct(guest, courseId, "{}").andReturn().getResponse().getStatus());
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

        Assertions.assertEquals(List.of(200, 200), statuses.stream().sorted().toList(),
                "동시 확정은 경합일 뿐 실패가 아니다. 실제=" + statuses);
        mockMvc.perform(get(LEAVES).header("X-Guest-Id", guest))
                .andExpect(jsonPath("$.data.usedDays").value(2.0))
                .andExpect(jsonPath("$.data.usages.length()").value(1));
    }
}
