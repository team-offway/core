package com.offway.core.itinerary.controller;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 코스 공유 링크(#143) — 공개 조회의 HTTP 계약.
 *
 * <p><b>클래스에 {@code @WithMockUser} 를 걸지 않는다.</b> 이 경로가 인증 게이트를 통과하는지가 검증
 * 대상이라, 인증을 걸어두면 정작 확인하려던 것이 가려진다. 코스를 만드는 준비 요청에만 {@code user()} 를 붙인다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CourseShareIntegrationTest {

    private static final String COURSES_URL = "/api/v1/courses";
    private static final String PUBLIC_URL = "/api/v1/public/courses/{shareToken}";
    private static final String GUEST_HEADER = "X-Guest-Id";

    // 정선(16) 당일치기 · 유효한 코스
    private static final String VALID_BODY =
            """
            { "regionId": 16, "density": "PACKED", "transport": "CAR", "days": [
              { "day": 1, "items": [
                {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c1","title":"장소1","lat":37.50,"lng":128.60,"travelMinutes":0},
                {"order":2,"timeOfDay":"LUNCH","kind":"FOOD","poiContentId":"c2","title":"맛집1","lat":37.51,"lng":128.61,"travelMinutes":15}
              ]}
            ]}""";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 저장하면_공유토큰이_함께_온다() throws Exception {
        mockMvc.perform(post(COURSES_URL)
                        .with(user("dev"))
                        .header(GUEST_HEADER, guest())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.shareToken", notNullValue()));
    }

    @Test
    void 공유토큰이면_인증_없이_코스를_본다() throws Exception {
        String token = saveAndGetToken(guest());

        mockMvc.perform(get(PUBLIC_URL, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.regionId").value(16))
                .andExpect(jsonPath("$.data.days[0].items[0].title").value("장소1"));
    }

    /** 링크를 받은 사람은 수정·삭제를 못 하므로 내부 순번을 알 이유가 없다. 알려주면 다른 경로를 두드릴 단서만 준다. */
    @Test
    void 공개_응답에는_내부_식별자와_토큰이_없다() throws Exception {
        String token = saveAndGetToken(guest());

        mockMvc.perform(get(PUBLIC_URL, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseId").doesNotExist())
                .andExpect(jsonPath("$.data.shareToken").doesNotExist());
    }

    @Test
    void 없는_토큰이면_404_다() throws Exception {
        mockMvc.perform(get(PUBLIC_URL, "AAAAAAAAAAAAAAAAAAAAAA"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-008"));
    }

    /**
     * 게시자가 지운 코스는 "없는 링크"(404)가 아니라 410 이다.
     *
     * <p>둘을 뭉치면 받은 사람이 자기가 링크를 잘못 눌렀다고 오해한다. 삭제됐다는 사실을 알려줘야 한다.
     */
    @Test
    void 게시자가_코스를_지우면_410_으로_삭제를_알린다() throws Exception {
        String guest = guest();
        String token = saveAndGetToken(guest);

        mockMvc.perform(delete(COURSES_URL + "/" + courseIdOf(guest))
                        .with(user("dev"))
                        .header(GUEST_HEADER, guest))
                .andExpect(status().isOk());

        mockMvc.perform(get(PUBLIC_URL, token))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.code").value("ITINERARY-009"));
    }

    /**
     * 공유 웹앱은 브라우저에서 부르므로 CORS 가 열려 있어야 한다. 설정만 넣고 확인하지 않으면 프론트가
     * 붙이는 순간에야 막힌 것을 알게 된다.
     */
    @Test
    void 공개_경로는_브라우저_오리진에_열려_있다() throws Exception {
        String token = saveAndGetToken(guest());

        mockMvc.perform(get(PUBLIC_URL, token).header("Origin", "https://offway.vercel.app"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://offway.vercel.app"))
                // 자격증명은 막는다 — 이 접두어 아래로 브라우저 쿠키·인증 헤더가 딸려가지 않게.
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    private String saveAndGetToken(String guest) throws Exception {
        String body = mockMvc.perform(post(COURSES_URL)
                        .with(user("dev"))
                        .header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.data.shareToken");
    }

    private long courseIdOf(String guest) throws Exception {
        String body = mockMvc.perform(get(COURSES_URL)
                        .with(user("dev"))
                        .header(GUEST_HEADER, guest))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.data[0].courseId")).longValue();
    }

    /** DB 격리: 롤백 대신 테스트마다 고유 게스트 ID 를 써서 "내 코스" 목록이 섞이지 않게 한다. */
    private static String guest() {
        return "share-" + UUID.randomUUID();
    }
}
