package com.offway.core.common.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 외부 API 사용량 집계(#123) — <b>재시작해도 남고, KST 자정에 리셋되는가</b>.
 *
 * <p>인메모리로 두면 재시작마다 0 이 되어 실제보다 여유 있게 보인다. 배포가 잦은 날일수록 실제 소진에 가까운데
 * 화면은 깨끗해지는, 정확히 반대 방향의 오차가 난다. 그래서 DB 에 둔다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ExternalApiQuotaIntegrationTest {

    /** 로컬 기본 자격증명(#122). 운영은 환경변수로 다른 값을 쓴다. */
    private static final String USERNAME = "dev";
    private static final String PASSWORD = "dev";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExternalApiCallRecorder recorder;

    @Autowired
    private ExternalApiCallRepository repository;

    @Test
    void 호출을_기록하면_오늘_누적이_는다() {
        LocalDate today = recorder.today();
        long before = repository.countsOn(today).getOrDefault(ExternalApi.TOUR_API, 0L);

        recorder.record(ExternalApi.TOUR_API);
        recorder.record(ExternalApi.TOUR_API);

        assertEquals(before + 2, repository.countsOn(today).get(ExternalApi.TOUR_API));
    }

    @Test
    void 날짜가_다르면_따로_센다() {
        // KST 자정을 넘기면 새 행이 되어 자연히 리셋된다. 날짜가 키라 별도 초기화 작업이 없다.
        LocalDate yesterday = recorder.today().minusDays(1);

        repository.recordAndCount(ExternalApi.AIR_KOREA, yesterday);

        assertEquals(1L, repository.countsOn(yesterday).get(ExternalApi.AIR_KOREA));
        assertEquals(0L, repository.countsOn(recorder.today()).getOrDefault(ExternalApi.AIR_KOREA, 0L));
    }

    @Test
    void 한도_현황을_전_API_에_대해_낸다() throws Exception {
        // 한 번도 안 부른 API 도 0 으로 나가야 한다 — 빠져 있으면 "안 쓴 것" 과 "안 센 것" 이 구분되지 않는다.
        mockMvc.perform(get("/api/v1/quotas").with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.apis.length()").value(ExternalApi.values().length))
                .andExpect(jsonPath("$.data.apis[?(@.api == 'TMAP_WAYPOINT')].limit").value(50))
                .andExpect(jsonPath("$.data.apis[?(@.api == 'AIR_KOREA')].limit").value(500));
    }

    @Test
    void 인증_없이는_한도_현황을_볼_수_없다() throws Exception {
        // 운영 수치는 팀만 본다. 공개 경로(/api/v1/public/**)가 아니라 인증 뒤에 둔다(#122).
        mockMvc.perform(get("/api/v1/quotas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 잔여는_한도를_넘겨도_음수가_되지_않는다() {
        // 화면이 -37 을 그리게 두지 않는다. 넘긴 사실은 error 로그가 알린다.
        assertEquals(0, ExternalApi.TMAP_WAYPOINT.remainingAfter(87));
        assertEquals(13, ExternalApi.TMAP_WAYPOINT.remainingAfter(37));
    }

    @Test
    void 기록이_실패해도_예외를_올리지_않는다() {
        // 사용량 집계는 관측이지 기능이 아니다. 여기서 던지면 외부 호출 자체가 막힌다.
        ExternalApiCallRecorder broken = new ExternalApiCallRecorder(null);

        broken.record(ExternalApi.TOUR_API);

        assertTrue(true, "예외 없이 지나가야 한다");
    }
}
