package com.offway.core.leave.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * "내 연차" 통합 테스트.
 *
 * <p>소유자를 테스트마다 다르게 쓴다 — 이 클래스는 DB 를 롤백하지 않아(공유 컨텍스트) 같은 키를 쓰면 앞 테스트의
 * 잔여 상태가 다음 시나리오로 새어 든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MyLeaveIntegrationTest {

    private static final String URL = "/api/v1/leaves/me";
    private static final String USAGES_URL = URL + "/usages";
    private static final String GUEST_HEADER = "X-Guest-Id";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 설정한_적_없으면_총0_내역없음으로_내려준다() throws Exception {
        // 없는 소유자를 404 로 돌려주면 클라이언트가 "처음 쓰는 사람" 을 예외로 다뤄야 한다.
        mockMvc.perform(get(URL).header(GUEST_HEADER, "leave-fresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.totalDays").value(0.0))
                .andExpect(jsonPath("$.data.usedDays").value(0.0))
                .andExpect(jsonPath("$.data.remainingDays").value(0.0))
                .andExpect(jsonPath("$.data.usages.length()").value(0));
    }

    @Test
    void 총_연차를_수정하면_남은_연차가_따라_바뀐다() throws Exception {
        String guest = "leave-update";

        mockMvc.perform(patch(URL).header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalDays\": 15}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDays").value(15.0))
                .andExpect(jsonPath("$.data.remainingDays").value(15.0));

        // 다시 수정 — 새 행이 생기지 않고 같은 소유자의 값이 바뀐다
        mockMvc.perform(patch(URL).header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalDays\": 12.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDays").value(12.5))
                .andExpect(jsonPath("$.data.remainingDays").value(12.5));
    }

    @Test
    void 사용내역을_쌓으면_남은_연차가_줄고_취소하면_되돌아온다() throws Exception {
        String guest = "leave-usage";
        mockMvc.perform(patch(URL).header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalDays\": 10}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(USAGES_URL).header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usedOn\": \"2026-05-08\", \"days\": 3, \"reason\": \"제주 여행\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.usedDays").value(3.0))
                .andExpect(jsonPath("$.data.remainingDays").value(7.0));

        // 취소는 행을 지우지 않고 음수 내역을 하나 더 쌓는다 — 언제 무엇이 취소됐는지가 남는다.
        mockMvc.perform(post(USAGES_URL).header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usedOn\": \"2026-05-09\", \"days\": -1, \"reason\": \"하루 취소\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.usedDays").value(2.0))
                .andExpect(jsonPath("$.data.remainingDays").value(8.0))
                .andExpect(jsonPath("$.data.usages.length()").value(2));
    }

    @Test
    void 반차는_0점5로_센다() throws Exception {
        String guest = "leave-half";
        mockMvc.perform(patch(URL).header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalDays\": 5}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(USAGES_URL).header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usedOn\": \"2026-05-07\", \"days\": 1.5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.remainingDays").value(3.5));
    }

    @Test
    void 남은_연차가_모자라도_막지_않고_음수로_내려준다() throws Exception {
        // 결정 #38 — 서버는 막지 않는다. 프론트가 경고하고 사용자가 확인하면 진행한다.
        String guest = "leave-over";
        mockMvc.perform(patch(URL).header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalDays\": 2}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(USAGES_URL).header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usedOn\": \"2026-05-08\", \"days\": 5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.remainingDays").value(-3.0));
    }

    @Test
    void 소유_키가_다르면_서로의_연차가_보이지_않는다() throws Exception {
        mockMvc.perform(patch(URL).header(GUEST_HEADER, "leave-owner-a")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalDays\": 20}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(URL).header(GUEST_HEADER, "leave-owner-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDays").value(0.0));
    }

    @Test
    void 총_연차가_0점5_단위가_아니면_400_LEAVE_009() throws Exception {
        mockMvc.perform(patch(URL).header(GUEST_HEADER, "leave-bad")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalDays\": 1.3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-009"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 총_연차가_음수면_400_LEAVE_009() throws Exception {
        mockMvc.perform(patch(URL).header(GUEST_HEADER, "leave-bad")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalDays\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-009"));
    }

    @Test
    void 사용_증감이_0이면_400_LEAVE_010() throws Exception {
        mockMvc.perform(post(USAGES_URL).header(GUEST_HEADER, "leave-bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usedOn\": \"2026-05-08\", \"days\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-010"));
    }

    @Test
    void 소유_키_헤더가_없으면_400_COMMON_400() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    /**
     * 빈 헤더({@code X-Guest-Id: " "})는 {@code @RequestHeader} 를 통과한다 — 헤더가 '있긴 있기' 때문이다.
     * 그대로 흘려보내면 도메인 불변식에서 터져 <b>COMMON-500</b> 이 나갔다. 클라이언트 계약 위반이 서버 버그로
     * 보고되던 자리라, 세 엔드포인트가 <b>같은 계약</b>을 쓰는지 함께 확인한다.
     */
    @Test
    void 빈_소유_키는_세_엔드포인트_모두_400_LEAVE_011() throws Exception {
        mockMvc.perform(get(URL).header(GUEST_HEADER, "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-011"));

        mockMvc.perform(patch(URL).header(GUEST_HEADER, "  ")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalDays\": 10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-011"));

        mockMvc.perform(post(USAGES_URL).header(GUEST_HEADER, "  ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usedOn\": \"2026-05-08\", \"days\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-011"));
    }

    @Test
    void 소유_키가_너무_길면_400_LEAVE_011() throws Exception {
        mockMvc.perform(get(URL).header(GUEST_HEADER, "x".repeat(65)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-011"));
    }
}
