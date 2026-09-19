package com.offway.core.transport.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 출발지를 코드로 받는 계약(#590) — 지역 추천 경로로 검증한다.
 *
 * <p><b>왜 추천으로 보나.</b> 세 자리(추천·코스 생성·재생성)가 같은
 * {@code OriginSuggestService#resolveOrDefault} 를 쓰고, 추천은 외부 stub 구성이 가장 가벼워 우선순위
 * 규칙 자체를 드러내기 좋다. 코스 생성 쪽 시나리오는 그쪽 통합 테스트가 이미 덮고 있다.
 *
 * <p>여기서 잠그는 것은 <b>출발지를 어디서 얻는가</b> 하나다 — 추천 결과의 내용이 아니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class OriginCodeOnCourseIntegrationTest {

    private static final String URL = "/api/v1/regions/recommendations";

    @Autowired
    private MockMvc mockMvc;

    private static String body(String originFields) {
        return """
                { %s "transport": "CAR", "maxReachMinutes": 420 }""".formatted(originFields);
    }

    @Test
    void 허브_코드로_출발지를_받는다() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"originCode\": \"TRAIN:NAT010000\",")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void 좌표_코드로도_받는다() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"originCode\": \"GEO:37.5665,126.9780\", \"originName\": \"분당구청\",")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void 구버전_앱의_좌표도_그대로_받는다() throws Exception {
        // 앱이 심사를 거쳐야 해서 위치 수집을 걷어낸 버전이 퍼지기까지 구버전이 한동안 남는다.
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"originLat\": 37.5665, \"originLng\": 126.9780,")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void 출발지를_아예_안_보내면_기본값으로_돈다() throws Exception {
        // 출발지를 고르기 전에도 추천을 보여주는 화면이 있다 — 버그가 아니라 정상 흐름이다.
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void 없는_허브_코드는_400_이다() throws Exception {
        // 조용히 기본값으로 떨어지면 고른 곳과 다른 데를 기준으로 추천이 나오고, 틀렸다는 사실이
        // 아무 흔적도 남지 않는다.
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"originCode\": \"TRAIN:NO_SUCH_STATION\",")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("TRANSPORT-001"))
                .andExpect(jsonPath("$.detail").value("출발지를 찾을 수 없습니다. 다시 선택해 주세요."));
    }

    @Test
    void 망가진_좌표_코드도_400_이다() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"originCode\": \"GEO:37.5665\",")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRANSPORT-001"));
    }

    @Test
    void 코드가_있으면_좌표보다_우선한다() throws Exception {
        // 둘을 함께 보내도 코드가 이긴다 — 없는 코드를 넣으면 좌표로 떨어지지 않고 400 이 되는 것이
        // 그 증거다. 조용히 좌표로 내려가면 이 계약이 깨진 것을 아무도 모른다.
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"originCode\": \"TRAIN:NO_SUCH_STATION\","
                                + " \"originLat\": 37.5665, \"originLng\": 126.9780,")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRANSPORT-001"));
    }
}
