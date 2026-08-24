package com.offway.core.user.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 인증 게이트 계약 — 8080 을 외부에 열 때 아무나 우리 외부 API 키를 태우지 못하게 막는다(#122).
 *
 * <p>여기서 검증하는 것은 "막힌다" 뿐 아니라 <b>막히는 모양</b>이다. Security 의 기본 401 은 필터 레벨이라
 * {@code ApiResponseBody} 래퍼를 타지 않는데, 그대로 두면 클라이언트가 응답 파싱에 실패한다.
 *
 * <p>local 프로파일 기본 계정({@code dev}/{@code dev})으로 검증한다 — 시크릿 없이 부팅되는 로컬 실행성이
 * 인증 도입으로 깨지지 않아야 한다.
 *
 * <p><b>소유 키가 {@code user_id} 로 옮겨간 뒤 경계가 하나 늘었다(#280).</b> 예전에는 "Basic 은 읽기만" 이
 * 전부였는데, 이제 <b>소유자가 있는 데이터는 읽기도 Bearer 만</b> 받는다. Basic 으로 들어온 요청은
 * principal 이 없어({@code @LoginUser} 는 JWT 가 넣은 것만 푼다) 대상을 정할 수 없고, 그대로 통과시키면
 * "빈 목록 200" 이나 NPE 500 이 나가는 조용한 실패가 된다. 그래서 이 클래스가 잠그는 것은 세 가지다 —
 * <b>무인증은 401</b>(배포 스모크의 게이트) · <b>Basic 은 소유 데이터에 못 닿는다</b>(403) ·
 * <b>그 밖의 읽기는 Basic 으로 여전히 열려 있다</b>(Swagger·스모크가 타는 경로).
 */
@SpringBootTest
@AutoConfigureMockMvc
class BasicAuthIntegrationTest {

    private static final String USERNAME = "dev";
    private static final String PASSWORD = "dev";

    /** 소유자가 없는 읽기 — Basic 으로 열려 있어야 하는 쪽. 사람이 브라우저로 서버를 들여다보는 경로다. */
    private static final String OPEN_READ_URL = "/api/v1/categories";

    /** 소유자가 있는 데이터의 읽기 — 이제 Bearer 만 받는다. */
    private static final String OWNED_READ_URL = "/api/v1/courses";

    /**
     * 소유자가 없는 <b>쓰기</b> — Basic 이 막히는 이유가 "소유 데이터라서" 가 아니라 "쓰기라서" 인 자리다.
     *
     * <p>소유 경로({@code /api/v1/courses} 등)로 쓰기를 시도하면 두 규칙이 겹쳐, 쓰기 금지가 풀려도 테스트가
     * 계속 초록일 수 있다. 겹치지 않는 경로를 골라 규칙 하나만 검증한다.
     */
    private static final String OPEN_WRITE_URL = "/api/v1/regions/recommendations";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 인증_없이_호출하면_401이다() throws Exception {
        // 배포 스모크가 이 성질을 게이트로 삼는다 — 401 이 아니면 롤백한다.
        mockMvc.perform(get(OPEN_READ_URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void 인증실패_응답도_공통_래퍼_모양을_지킨다() throws Exception {
        // 필터 레벨 기본 401 은 빈 본문이라 클라이언트가 파싱에 실패한다. 래퍼로 감싸 계약을 맞춘다.
        mockMvc.perform(get(OPEN_READ_URL))
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
        mockMvc.perform(get(OPEN_READ_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Basic")));
    }

    @Test
    void 잘못된_비밀번호는_401이다() throws Exception {
        mockMvc.perform(get(OPEN_READ_URL).with(httpBasic(USERNAME, "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 모르는_사용자는_401이다() throws Exception {
        mockMvc.perform(get(OPEN_READ_URL).with(httpBasic("intruder", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 소유자가_없는_읽기는_Basic_으로_열려_있다() throws Exception {
        // Swagger 로 명세를 보는 것도, 배포 스모크가 적재를 확인하는 것도 이 경로를 탄다. 소유 데이터를
        // 잠그면서 여기까지 잠그면 사람이 서버를 들여다볼 수단이 함께 사라진다.
        mockMvc.perform(get(OPEN_READ_URL).with(httpBasic(USERNAME, PASSWORD)))
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
        mockMvc.perform(post(OPEN_WRITE_URL)
                        .with(httpBasic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("COMMON-403"))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    /**
     * <b>Basic 으로는 소유 데이터를 읽지도 못한다</b>(#280) — 이 전환이 새로 그은 경계다.
     *
     * <p>이 경로들은 요청 헤더가 아니라 access 토큰이 넣은 principal 로 대상을 정한다. Basic 요청은 그
     * principal 이 null 이라 주인 없는 조회가 돌아 "빈 목록 200"(있지도 않은 남의 화면을 성공으로 그린다)이나
     * NPE 500 이 된다. 규약이 막는 조용한 실패라, 인가 단계에서 403 으로 끊는 것이 의도다.
     *
     * <p>{@code /api/v1/devices} 는 GET 이 없어 여기에 넣지 않는다 — 그쪽은 쓰기 금지 규칙이 이미 잡는다.
     */
    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/courses", "/api/v1/leaves/me", "/api/v1/notifications", "/api/v1/users/me"})
    void Basic_으로는_소유_데이터를_읽지_못한다(String ownedUrl) throws Exception {
        mockMvc.perform(get(ownedUrl).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("COMMON-403"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 소유_데이터도_자격증명이_없으면_403이_아니라_401이다() throws Exception {
        // 403 으로 답하면 "자격증명을 내라" 는 신호가 사라져 앱은 재로그인할 이유를 못 찾고, 무인증 GET 이
        // 401 이라는 배포 스모크의 게이트도 이 경로에서 깨진다.
        mockMvc.perform(get(OWNED_READ_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-401"));
    }
}
