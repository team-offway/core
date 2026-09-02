package com.offway.core.common.external.controller;

import static com.offway.core.user.config.TestLogins.basicOnly;
import static com.offway.core.user.config.TestLogins.loginAs;
import static com.offway.core.user.config.TestLogins.loginAsAdmin;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.common.external.ExternalApi;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외부 API 연동 현황(#398).
 *
 * <p>이 화면은 <b>우리가 어떤 외부에 얼마나 기대고 있는지</b>를 통째로 드러낸다 — 한도·소진율·주체·
 * 그리고 어느 화면이 무엇을 부르는지까지. 팀 밖에 나갈 이유가 없어 읽기까지 어드민으로 막는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminExternalApiIntegrationTest {

    private static final String URL = "/api/v1/admin/external-apis";

    @Autowired
    private MockMvc mockMvc;

    // ── 누가 들어올 수 있나 ────────────────────────────────────────────────

    @Test
    void 로그인하지_않으면_못_들어온다() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void 일반_사용자_토큰으로는_못_들어온다() throws Exception {
        mockMvc.perform(get(URL).with(loginAs(UUID.randomUUID()))).andExpect(status().isForbidden());
    }

    /** Basic 은 사람이 Swagger 로 서버를 들여다보는 수단이지 백오피스 자격증명이 아니다. */
    @Test
    void Basic_자격증명으로도_못_본다() throws Exception {
        mockMvc.perform(get(URL).with(basicOnly())).andExpect(status().isForbidden());
    }

    // ── 무엇이 나오나 ────────────────────────────────────────────────────

    /**
     * <b>연동은 하나도 빠지지 않는다.</b> 한 번도 안 부른 API 도 0 으로 나와야 한다 — 붙여 놓고
     * 안 쓰는 연동이 있는지가 이 화면이 답해야 할 질문 중 하나다.
     */
    @Test
    void 어드민은_연동_전부를_본다() throws Exception {
        mockMvc.perform(get(URL).with(loginAsAdmin(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.apis.length()").value(ExternalApi.values().length))
                .andExpect(jsonPath("$.data.apis[0].name").exists())
                .andExpect(jsonPath("$.data.apis[0].dailyLimit").value(greaterThan(0)));
    }

    /** 기본 기간은 14일이고, 기록이 없는 날도 0 으로 채워 나온다. */
    @Test
    void 기간을_안_주면_14일을_돌려준다() throws Exception {
        mockMvc.perform(get(URL).with(loginAsAdmin(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days").value(14))
                .andExpect(jsonPath("$.data.daily.length()").value(14));
    }

    /**
     * <b>상한을 넘겨도 거절하지 않고 자른다.</b> 조회 화면에서 잘못된 값은 클라이언트 실수지 계약
     * 위반이 아니고, 400 으로 끊으면 화면이 통째로 빈다(목록 페이지네이션과 같은 판단).
     */
    @Test
    void 기간이_상한을_넘으면_잘라서_돌려준다() throws Exception {
        mockMvc.perform(get(URL).param("days", "100000").with(loginAsAdmin(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days").value(90));
    }

    @Test
    void 기간이_0_이하면_하루로_올려서_돌려준다() throws Exception {
        mockMvc.perform(get(URL).param("days", "-5").with(loginAsAdmin(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days").value(1));
    }

    /**
     * <b>데이터 출처 지도가 함께 나온다.</b> 이 값이 없으면 화면은 숫자만 보여 주고, "그래서 이걸
     * 어디서 쓰나" 는 다시 코드를 읽어야 안다.
     */
    @Test
    void 쓰는_화면과_방식이_함께_실린다() throws Exception {
        mockMvc.perform(get(URL).with(loginAsAdmin(UUID.randomUUID())))
                .andExpect(status().isOk())
                // 코스 생성이 가장 많이 태우는 화면이라, 이 값이 빠지면 화면이 숫자만 보여 준다.
                .andExpect(jsonPath("$.data.apis[*].flows[*].screen", hasItem("코스 생성")))
                .andExpect(jsonPath("$.data.apis[*].flows[*].mode", hasItem("실호출")))
                // 화면이 클래스명으로 쓰는 값이라 한글 라벨과 따로 나가야 한다.
                .andExpect(jsonPath("$.data.apis[*].flows[*].modeName", hasItem("LIVE")));
    }

    // ── 바꾸기(#403) ─────────────────────────────────────────────────────

    /** 바꾼 뒤에도 <b>현황 전체</b>를 돌려준다 — 화면이 나머지를 다시 묻지 않게. */
    @Test
    void 캐시를_끄면_그_자리에서_반영된다() throws Exception {
        mockMvc.perform(patch(URL + "/{api}", "TOUR_API")
                        .with(loginAsAdmin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cacheEnabled\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.apis[?(@.name == 'TOUR_API')].cacheEnabled", hasItem(false)))
                .andExpect(jsonPath("$.data.apis[?(@.name == 'TOUR_API')].settingDefault", hasItem(false)));
    }

    @Test
    void 배치_상한을_걸_수_있다() throws Exception {
        mockMvc.perform(patch(URL + "/{api}", "TOUR_API")
                        .with(loginAsAdmin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cacheEnabled\": true, \"batchLimit\": 700}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.apis[?(@.name == 'TOUR_API')].batchLimit", hasItem(700)));
    }

    /**
     * 일일 한도보다 큰 상한은 <b>무제한과 같은데 화면에는 제한이 걸린 것처럼 보인다.</b>
     * 조용히 뜻이 다른 값을 받지 않는다.
     */
    @Test
    void 일일_한도를_넘는_상한은_400_이다() throws Exception {
        mockMvc.perform(patch(URL + "/{api}", "TOUR_API")
                        .with(loginAsAdmin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchLimit\": 999999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXTAPI-003"));
    }

    @Test
    void 음수_상한은_400_이다() throws Exception {
        mockMvc.perform(patch(URL + "/{api}", "TOUR_API")
                        .with(loginAsAdmin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchLimit\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXTAPI-002"));
    }

    @Test
    void 모르는_연동은_400_이다() throws Exception {
        mockMvc.perform(patch(URL + "/{api}", "NOT_AN_API")
                        .with(loginAsAdmin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cacheEnabled\": false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXTAPI-001"));
    }

    /**
     * <b>선택 필드를 빼도 400 이 되지 않는다.</b> Jackson 3 은 선택 필드가 primitive 면 생략됐을 때
     * 매핑을 깨뜨려, 어드민이 한 칸만 보낸 것이 "값 오류" 로 보고된다(#354 에서 겪었다).
     */
    @Test
    void 빈_본문을_보내도_기본값으로_받는다() throws Exception {
        mockMvc.perform(patch(URL + "/{api}", "TOUR_API")
                        .with(loginAsAdmin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.apis[?(@.name == 'TOUR_API')].cacheEnabled", hasItem(true)));
    }

    @Test
    void 일반_사용자는_설정을_못_바꾼다() throws Exception {
        mockMvc.perform(patch(URL + "/{api}", "TOUR_API")
                        .with(loginAs(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cacheEnabled\": false}"))
                .andExpect(status().isForbidden());
    }

    /** 배치는 이름이 코드 상수라 서버가 목록을 들고 있지 않다 — 오타는 아무 배치도 안 막아 해가 없다. */
    @Test
    void 배치를_멈출_수_있다() throws Exception {
        mockMvc.perform(patch(URL + "/batches/{name}", "poi-intro-refresh")
                        .with(loginAsAdmin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }
}
