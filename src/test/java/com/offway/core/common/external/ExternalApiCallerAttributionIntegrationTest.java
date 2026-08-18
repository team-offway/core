package com.offway.core.common.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 외부 호출을 <b>누가</b> 태웠는지 가른다(#285).
 *
 * <p>#257 의 한도 알림은 초과 사실만 말해, 받고도 할 수 있는 일이 없었다 — 배치가 태웠는지 코스 생성이
 * 태웠는지 몰라 다음 행동이 안 정해졌다. 이 테스트는 그 내역이 실제로 갈려 저장되고 응답에 나오는지를 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ExternalApiCallerAttributionIntegrationTest {

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
    void 심은_주체로_갈라_센다() {
        LocalDate today = recorder.today();
        Caller batch = Caller.of("중심관광지배치");
        Caller detail = Caller.of("장소상세");

        CallerContext.run(batch, () -> {
            recorder.record(ExternalApi.TOUR_DATA_LAB);
            recorder.record(ExternalApi.TOUR_DATA_LAB);
        });
        CallerContext.run(detail, () -> recorder.record(ExternalApi.TOUR_DATA_LAB));

        Map<String, Long> counts = repository.callerCountsOn(ExternalApi.TOUR_DATA_LAB, today);
        assertEquals(2L, counts.get(batch.name()));
        assertEquals(1L, counts.get(detail.name()));
    }

    /**
     * 맥락을 안 심은 호출은 미상으로 모인다.
     *
     * <p><b>버킷으로 남기는 것이 요점이다.</b> 맥락 심기를 빠뜨린 경로가 생기면 미상 비중이 커져 눈에 보인다.
     * 아무 주체에나 붙이면 조용히 틀린 값이 되어 오히려 나쁘다.
     */
    @Test
    void 주체를_모르면_미상으로_센다() {
        LocalDate today = recorder.today();
        long before = repository.callerCountsOn(ExternalApi.TRAIN_INFO, today)
                .getOrDefault(Caller.UNKNOWN.name(), 0L);

        recorder.record(ExternalApi.TRAIN_INFO);

        assertEquals(before + 1,
                repository.callerCountsOn(ExternalApi.TRAIN_INFO, today).get(Caller.UNKNOWN.name()));
    }

    /** 총량은 #257 이 알림 단계를 잠그는 근거라, 내역을 갈라도 그대로여야 한다. */
    @Test
    void 총량은_주체를_갈라도_그대로다() {
        LocalDate today = recorder.today();
        long before = repository.countsOn(today).getOrDefault(ExternalApi.BUS_STOP, 0L);

        CallerContext.run(Caller.of("코스생성"), () -> recorder.record(ExternalApi.BUS_STOP));
        CallerContext.run(Caller.of("장소상세"), () -> recorder.record(ExternalApi.BUS_STOP));

        assertEquals(before + 2, repository.countsOn(today).get(ExternalApi.BUS_STOP));
    }

    /**
     * 요청이 끝나면 맥락이 비워진다.
     *
     * <p>안 비우면 톰캣 풀로 돌아간 스레드가 <b>다음 요청에 남의 주체를 물려준다</b> — 미상보다 나쁘다.
     * 조용히 틀린 값이 되어 알림 내역을 못 믿게 만든다. (패턴을 어떻게 뽑는지는
     * {@code CallerAttributionInterceptorTest} 가 본다.)
     */
    @Test
    void 요청이_끝나면_맥락이_비워진다() throws Exception {
        mockMvc.perform(get("/api/v1/quotas").with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk());

        assertEquals(Caller.UNKNOWN, CallerContext.current());
    }

    @Test
    void 한도_현황_응답이_주체_내역을_함께_낸다() throws Exception {
        CallerContext.run(Caller.of("지역콘텐츠배치"), () -> recorder.record(ExternalApi.TOUR_API));

        mockMvc.perform(get("/api/v1/quotas").with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.apis[?(@.api == 'TOUR_API')].callers[?(@.caller == '지역콘텐츠배치')]")
                        .exists());
    }

    /** 내역 기록이 실패해도 외부 호출은 막지 않는다 — 집계는 관측이지 기능이 아니다. */
    @Test
    void 내역_기록이_실패해도_예외를_올리지_않는다() {
        ExternalApiCallRecorder broken = new ExternalApiCallRecorder(null, message -> {
        });

        CallerContext.run(Caller.of("코스생성"), () -> broken.record(ExternalApi.TOUR_API));

        assertTrue(true, "예외 없이 지나가야 한다");
    }

    @Test
    void 날짜가_다르면_내역도_따로_센다() {
        LocalDate yesterday = recorder.today().minusDays(1);
        Caller batch = Caller.of("갤러리사진배치");

        repository.recordCaller(ExternalApi.TOUR_GALLERY, yesterday, batch);

        assertEquals(1L, repository.callerCountsOn(ExternalApi.TOUR_GALLERY, yesterday).get(batch.name()));
        assertFalse(repository.callerCountsOn(ExternalApi.TOUR_GALLERY, recorder.today()).containsKey(batch.name()));
    }
}
