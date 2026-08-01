package com.offway.core.itinerary.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

// DB 격리: 롤백 대신 테스트마다 고유 게스트 ID 를 써서 "내 코스" 목록이 섞이지 않게 한다.
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class CourseStorageIntegrationTest {

    private static final String URL = "/api/v1/courses";

    // 정선(16) 당일치기 · 유효한 코스(첫 슬롯 이동 0, 순서 연속)
    private static final String VALID_BODY = """
            { "regionId": 16, "density": "PACKED", "transport": "CAR", "days": [
              { "day": 1, "items": [
                {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c1","title":"장소1","lat":37.50,"lng":128.60,"travelMinutes":0},
                {"order":2,"timeOfDay":"LUNCH","kind":"FOOD","poiContentId":"c2","title":"맛집1","lat":37.51,"lng":128.61,"travelMinutes":15}
              ]}
            ]}""";

    @Autowired
    private MockMvc mockMvc;

    /** 테스트마다 고유한 게스트 ID — 롤백 없이 "내 코스" 목록이 이전 실행과 섞이지 않게. */
    private static String uniqueGuest() {
        return "guest-" + UUID.randomUUID();
    }

    @Test
    void 코스를_저장하면_201로_courseId를_준다() throws Exception {
        mockMvc.perform(post(URL).header("X-Guest-Id", uniqueGuest()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.courseId").isNumber())
                .andExpect(jsonPath("$.data.regionId").value(16))
                .andExpect(jsonPath("$.data.days[0].items[0].travelMinutes").value(0));
    }

    @Test
    void 저장한_코스가_내_코스_목록과_상세에_나온다() throws Exception {
        String guest = uniqueGuest();
        String saved = mockMvc.perform(post(URL).header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int courseId = JsonPath.read(saved, "$.data.courseId");

        mockMvc.perform(get(URL).header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].courseId").value(courseId))
                .andExpect(jsonPath("$.data[0].placeCount").value(2));

        mockMvc.perform(get(URL + "/{id}", courseId).header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseId").value(courseId))
                .andExpect(jsonPath("$.data.days[0].items.length()").value(2));
    }

    @Test
    void 남의_코스는_상세로_볼_수_없다_404() throws Exception {
        String owner = uniqueGuest();
        String saved = mockMvc.perform(post(URL).header("X-Guest-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int courseId = JsonPath.read(saved, "$.data.courseId");

        // 다른 게스트가 같은 courseId 를 조회 → 존재 여부를 흘리지 않도록 404
        mockMvc.perform(get(URL + "/{id}", courseId).header("X-Guest-Id", uniqueGuest()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-003"));
    }

    @Test
    void 게스트_헤더가_없으면_400() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 게스트_ID가_공백이면_400() throws Exception {
        // 빈 게스트 ID 를 허용하면 모든 요청이 한 묶음을 공유 → 도메인이 막고 400
        mockMvc.perform(post(URL).header("X-Guest-Id", "  ").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ITINERARY-002"));
    }

    @Test
    void 슬롯_순서가_불연속이면_400_ITINERARY_002() throws Exception {
        String invalid = """
                { "regionId": 16, "density": "PACKED", "transport": "CAR", "days": [
                  { "day": 1, "items": [
                    {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c1","title":"장소1","lat":37.5,"lng":128.6,"travelMinutes":0},
                    {"order":3,"timeOfDay":"LUNCH","kind":"FOOD","poiContentId":"c2","title":"맛집1","lat":37.51,"lng":128.61,"travelMinutes":15}
                  ]}
                ]}""";

        mockMvc.perform(post(URL).header("X-Guest-Id", uniqueGuest()).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ITINERARY-002"));
    }

    @Test
    void 없는_코스_상세는_404_ITINERARY_003() throws Exception {
        mockMvc.perform(get(URL + "/{id}", 999999).header("X-Guest-Id", uniqueGuest()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-003"));
    }

    @Test
    void 코스를_삭제하면_목록과_상세에서_사라진다() throws Exception {
        String guest = uniqueGuest();
        String saved = mockMvc.perform(post(URL).header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int courseId = JsonPath.read(saved, "$.data.courseId");

        // 204 를 쓰지 않는다 — 응답 래퍼가 항상 body 를 만든다.
        mockMvc.perform(delete(URL + "/{id}", courseId).header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(get(URL).header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get(URL + "/{id}", courseId).header("X-Guest-Id", guest))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-003"));
    }

    @Test
    void 남의_코스는_삭제할_수_없고_그대로_남는다_404() throws Exception {
        String owner = uniqueGuest();
        String saved = mockMvc.perform(post(URL).header("X-Guest-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int courseId = JsonPath.read(saved, "$.data.courseId");

        // 403 이 아니라 404 — 403 이면 "그 ID 는 존재한다" 를 알려주는 셈이다.
        mockMvc.perform(delete(URL + "/{id}", courseId).header("X-Guest-Id", uniqueGuest()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-003"));

        // 거부로 끝나야 한다 — 주인 것이 지워졌으면 안 된다
        mockMvc.perform(get(URL + "/{id}", courseId).header("X-Guest-Id", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseId").value(courseId));
    }

    @Test
    void 없는_코스_삭제는_404_ITINERARY_003() throws Exception {
        mockMvc.perform(delete(URL + "/{id}", 999999).header("X-Guest-Id", uniqueGuest()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-003"));
    }

    @Test
    void 같은_코스를_두_번_삭제하면_두_번째는_404() throws Exception {
        String guest = uniqueGuest();
        String saved = mockMvc.perform(post(URL).header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int courseId = JsonPath.read(saved, "$.data.courseId");

        mockMvc.perform(delete(URL + "/{id}", courseId).header("X-Guest-Id", guest))
                .andExpect(status().isOk());
        // 더블클릭·재시도 — 이미 없으니 없는 코스와 같은 답이다
        mockMvc.perform(delete(URL + "/{id}", courseId).header("X-Guest-Id", guest))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-003"));
    }

    @Test
    void 같은_코스를_동시에_삭제해도_500이_나지_않는다() throws Exception {
        String guest = uniqueGuest();
        String saved = mockMvc.perform(post(URL).header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int courseId = JsonPath.read(saved, "$.data.courseId");

        int threads = 2;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.List<Integer> statuses = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        // 요청 단위 mock 인증 — @WithMockUser 는 현재 스레드 전용이라 이 요청엔 안 닿는다.
                        // 실제 계정을 쓰지 않으므로 운영 자격증명이 바뀌어도 이 테스트는 그대로다.
                        statuses.add(mockMvc.perform(delete(URL + "/{id}", courseId)
                                        .header("X-Guest-Id", guest)
                                        .with(user("test")))
                                .andReturn().getResponse().getStatus());
                    } catch (Exception e) {
                        statuses.add(-1);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            org.junit.jupiter.api.Assertions.assertTrue(done.await(20, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        // 하나는 지우고 하나는 "없다" 여야 한다 — 순차 재삭제와 같은 계약이다. 500 이 섞이면 안 된다.
        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.List.of(200, 404), statuses.stream().sorted().toList(),
                "동시 삭제는 경합일 뿐 실패가 아니다. 실제=" + statuses);
    }
}
