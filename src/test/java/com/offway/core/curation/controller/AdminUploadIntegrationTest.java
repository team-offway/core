package com.offway.core.curation.controller;

import static com.offway.core.user.config.TestLogins.basicOnly;
import static com.offway.core.user.config.TestLogins.loginAs;
import static com.offway.core.user.config.TestLogins.loginAsAdmin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.user.domain.AdminAccount;
import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.User;
import com.offway.core.user.domain.UserIdentity;
import com.offway.core.user.repository.AdminAccountJpaRepository;
import com.offway.core.user.repository.UserIdentityRepository;
import com.offway.core.user.repository.UserJpaRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 백오피스 이미지 업로드 자리 발급의 HTTP 계약(#377).
 *
 * <p>여기서 잠그는 것은 <b>누가 발급받을 수 있나</b>와 <b>무엇을 거절하나</b> 다. 발급된 주소는 그 자체로
 * 버킷에 쓸 수 있는 권한이라, 아무나 받아 가면 인증이 있으나 마나다.
 *
 * <p><b>실제 S3 를 부르지 않는다.</b> 테스트 환경에는 자격증명이 없어 어댑터가 비활성으로 뜨고, 그
 * 상태가 곧 이 클래스가 확인하려는 것 중 하나다 — 키 없이도 부팅되고 발급만 실패해야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminUploadIntegrationTest {

    private static final String URL = "/api/v1/admin/uploads";

    private static final String VALID_BODY = """
            { "contentType": "image/png", "contentLength": 204800 }""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminAccountJpaRepository adminAccountJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private UserIdentityRepository userIdentityRepository;

    // ── 누가 받을 수 있나 ────────────────────────────────────────────────

    @Test
    void 자격증명이_없으면_401이다() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 일반_사용자_토큰으로는_403이다() throws Exception {
        // 발급된 주소는 그 자체로 버킷 쓰기 권한이다. 로그인만 했다고 내주면 누구나 우리 버킷에 쓴다.
        mockMvc.perform(post(URL)
                        .with(loginAs(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void Basic_자격증명으로는_403이다() throws Exception {
        // Basic 은 사람이 Swagger 로 서버를 들여다보는 수단이지 백오피스 자격증명이 아니다.
        mockMvc.perform(post(URL)
                        .with(basicOnly())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    // ── 무엇을 거절하나 ────────────────────────────────────────────────

    @Test
    void 허용하지_않는_종류는_400과_도메인_code_로_거절한다() throws Exception {
        // SVG 는 스크립트를 품을 수 있어 우리 도메인에서 열리면 XSS 가 된다.
        mockMvc.perform(post(URL)
                        .with(loginAsAdmin(whitelistedAdmin("업로드 검수")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "contentType": "image/svg+xml", "contentLength": 1024 }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("CURATION-007"));
    }

    @Test
    void 상한을_넘는_크기는_400과_도메인_code_로_거절한다() throws Exception {
        mockMvc.perform(post(URL)
                        .with(loginAsAdmin(whitelistedAdmin("업로드 검수")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "contentType": "image/png", "contentLength": 5242881 }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURATION-008"));
    }

    @Test
    void 크기가_0이면_요청_검증에서_400이다() throws Exception {
        // 도메인까지 가기 전에 Bean Validation 이 먼저 끊는다 — 그래서 code 는 도메인 것이 아니다.
        mockMvc.perform(post(URL)
                        .with(loginAsAdmin(whitelistedAdmin("업로드 검수")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "contentType": "image/png", "contentLength": 0 }"""))
                .andExpect(status().isBadRequest());
    }

    // ── 저장소가 없을 때 ────────────────────────────────────────────────

    /**
     * 자격증명이 없어도 <b>부팅은 되고</b> 발급만 실패한다(로컬 실행성 불변식).
     *
     * <p>이 테스트가 도는 것 자체가 절반의 확인이다 — 어댑터가 부팅을 막았다면 컨텍스트가 안 올라와 이
     * 클래스 전체가 실행되지 않는다. 나머지 절반은 그 실패가 <b>500 이 아니라 사유가 있는 응답</b>으로
     * 나가는 것이다.
     */
    @Test
    void 저장소_자격증명이_없으면_사유를_담아_502로_답한다() throws Exception {
        mockMvc.perform(post(URL)
                        .with(loginAsAdmin(whitelistedAdmin("업로드 검수")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.code").value("CURATION-009"))
                // 어드민이 이 문구를 보고 다음 행동을 정한다 — 주소 붙여넣기로 넘어가면 된다
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    /** 화이트리스트에 오른 어드민을 만든다 — 키가 우리 사용자 id 가 아니라 provider 식별자다. */
    private UUID whitelistedAdmin(String label) {
        String subject = "admin-" + UUID.randomUUID();
        UUID userId = userJpaRepository.save(User.withNickname(label)).getId();
        userIdentityRepository.save(UserIdentity.link(userId, AuthProvider.KAKAO, subject));
        adminAccountJpaRepository.save(AdminAccount.builder()
                .provider(AuthProvider.KAKAO)
                .providerUserId(subject)
                .label(label)
                .build());
        return userId;
    }
}
