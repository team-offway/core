package com.offway.core.user.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 인증 게이트 계약 — 8080 을 외부에 열 때 아무나 우리 외부 API 키를 태우지 못하게 막는다(#122).
 *
 * <p>여기서 검증하는 것은 "막힌다" 뿐 아니라 <b>막히는 모양</b>이다. Security 의 기본 401 은 필터 레벨이라
 * {@code ApiResponseBody} 래퍼를 타지 않는데, 그대로 두면 클라이언트가 응답 파싱에 실패한다.
 *
 * <p>local 프로파일 기본 계정({@code dev}/{@code dev})으로 검증한다 — 시크릿 없이 부팅되는 로컬 실행성이
 * 인증 도입으로 깨지지 않아야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BasicAuthIntegrationTest {

    private static final String USERNAME = "dev";
    private static final String PASSWORD = "dev";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 인증_없이_호출하면_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/categories")).andExpect(status().isUnauthorized());
    }

    @Test
    void 인증실패_응답도_공통_래퍼_모양을_지킨다() throws Exception {
        // 필터 레벨 기본 401 은 빈 본문이라 클라이언트가 파싱에 실패한다. 래퍼로 감싸 계약을 맞춘다.
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("COMMON-401"))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 인증실패_응답은_브라우저가_팝업을_띄울_수_있게_challenge를_준다() throws Exception {
        // 이 엔트리 포인트가 Security 기본 Basic 엔트리 포인트를 대체하므로, 여기서 안 붙이면 헤더가 아예
        // 나가지 않는다 → 브라우저 팝업이 없어 사람이 Swagger 를 열 수단이 사라진다(로그인 화면도 없다).
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Basic")));
    }

    @Test
    void 잘못된_비밀번호는_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/categories").with(httpBasic(USERNAME, "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 모르는_사용자는_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/categories").with(httpBasic("intruder", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 올바른_자격증명이면_통과한다() throws Exception {
        mockMvc.perform(get("/api/v1/categories").with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    /**
     * Basic 으로는 <b>읽기만</b> 된다 — 브라우저가 자동으로 붙이는 자격증명으로 상태를 바꾸지 못하게 한다.
     *
     * <p>브라우저는 캐시된 Basic 자격증명을 교차 출처 쓰기 요청에도 보내고, 공개 GET 경로의 CORS 제한은 그
     * 전송을 막지 못한다. 이 서비스는 CSRF 토큰을 쓰지 않는 무상태 API 라, 막는 자리가 여기다.
     */
    @Test
    void Basic_으로는_쓰기를_못_한다() throws Exception {
        mockMvc.perform(post("/api/v1/courses")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .header("X-Guest-Id", "basic-write-attempt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
