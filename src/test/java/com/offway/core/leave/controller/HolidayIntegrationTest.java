package com.offway.core.leave.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.leave.domain.HolidayException;
import com.offway.core.leave.infrastructure.holiday.HolidayClient;
import com.offway.core.leave.infrastructure.holiday.StubHolidayClient;
import com.offway.core.leave.service.LeaveService;
import java.time.LocalDate;
import java.time.Year;
import java.util.Set;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 공휴일 목록 API(#317).
 *
 * <p>앱의 로컬 폴백을 서버와 같은 답으로 맞추려는 API 라, 검증의 초점도 <b>틀린 답을 조용히 주지 않는가</b>다 —
 * 실패를 빈 목록으로 돌려주면 앱이 공휴일을 평일로 세어 연차를 과다 계산한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class HolidayIntegrationTest {

    private static final String URL = "/api/v1/holidays";

    /** 올해를 쓴다 — 허용 범위가 "지금 기준 지난해~내년" 이라 고정 연도로 두면 언젠가 범위 밖이 된다. */
    private static final int THIS_YEAR = Year.now().getValue();

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
        StubHolidayClient stubHolidayClient() {
            return new StubHolidayClient();
        }
    }

    /**
     * 공휴일 동작을 정하고 캐시를 비운다 — <b>stub 지정과 한 몸으로 묶는다</b>.
     *
     * <p>캐시가 공유 싱글톤이라 앞 테스트가 남긴 값이 이 시나리오로 샌다. 그렇다고 셋업 hook 에 두면
     * 캐시를 쓰지 않는 테스트(입력 검증)까지 끌려들고, 무엇보다 각 테스트가 자기 상태를 본문에서
     * 만든다는 규약과 어긋난다. 비우는 시점을 stub 지정과 붙여두면 둘이 갈릴 수 없다.
     */
    private void holidays(BiFunction<Integer, Integer, Set<LocalDate>> behavior) {
        holidayClient.respond(behavior);
        leaveService.evictCache();
    }

    @Test
    void 한_해의_공휴일을_날짜순으로_준다() throws Exception {
        LocalDate liberation = LocalDate.of(THIS_YEAR, 8, 15);
        LocalDate newYear = LocalDate.of(THIS_YEAR, 1, 1);
        holidays((year, month) -> switch (month) {
            case 1 -> Set.of(newYear);
            case 8 -> Set.of(liberation);
            default -> Set.of();
        });

        mockMvc.perform(get(URL).param("year", String.valueOf(THIS_YEAR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.year").value(THIS_YEAR))
                // 무엇을 물어서 받은 답인지 응답이 스스로 말한다 — 앱이 캐시에 담아 둘 값이라 짝이 어긋나면
                // 다른 해의 공휴일로 계산하게 된다.
                .andExpect(jsonPath("$.data.dates.length()").value(2))
                .andExpect(jsonPath("$.data.dates[0]").value(newYear.toString()))
                .andExpect(jsonPath("$.data.dates[1]").value(liberation.toString()));
    }

    @Test
    void 공휴일이_없는_해는_빈_배열이다() throws Exception {
        // 빈 배열이 "없음" 을 뜻하려면, 실패는 반드시 다른 모양이어야 한다(아래 502 테스트가 그 짝이다).
        holidays((year, month) -> Set.of());

        mockMvc.perform(get(URL).param("year", String.valueOf(THIS_YEAR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dates.length()").value(0));
    }

    @Test
    void 공휴일_조회에_실패하면_빈_배열이_아니라_502다() throws Exception {
        // **이 테스트가 이 API 의 핵심이다.** 실패를 빈 목록으로 답하면 앱은 그것을 "공휴일 없는 해" 로 읽고
        // 공휴일을 평일로 세어 차감일을 과다 계산한다 — 조용히 틀리는 쪽이다.
        holidays((year, month) -> {
            throw HolidayException.lookupFailed(new IllegalStateException("특일정보 장애"));
        });

        mockMvc.perform(get(URL).param("year", String.valueOf(THIS_YEAR)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 범위_밖_연도는_400_LEAVE_015() throws Exception {
        // 적재 창 밖이라 한 요청이 외부 호출 열두 번이 된다. 거절을 조용히 하지 않는 이유는 위와 같다.
        mockMvc.perform(get(URL).param("year", String.valueOf(THIS_YEAR + 5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-015"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void year_가_없거나_숫자가_아니면_400이다() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("year", "올해")).andExpect(status().isBadRequest());
    }
}
