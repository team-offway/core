package com.offway.core.curation.controller;

import static com.offway.core.user.config.TestLogins.basicOnly;
import static com.offway.core.user.config.TestLogins.loginAs;
import static com.offway.core.user.config.TestLogins.loginAsAdmin;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.offway.core.common.response.Paging;
import com.offway.core.curation.domain.CuratedLink;
import com.offway.core.curation.domain.Surface;
import com.offway.core.curation.repository.CuratedLinkJpaRepository;
import com.offway.core.user.domain.AdminAccount;
import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.User;
import com.offway.core.user.domain.UserIdentity;
import com.offway.core.user.repository.AdminAccountJpaRepository;
import com.offway.core.user.repository.UserIdentityRepository;
import com.offway.core.user.repository.UserJpaRepository;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 백오피스 큐레이션 CRUD 의 HTTP 계약(#342).
 *
 * <p>여기서 잠그는 것은 둘이다 — <b>어드민이 아닌 요청이 못 들어오는 것</b>과 <b>어드민이 고친 값이 앱
 * 응답까지 이어지는 것</b>. 값 검증 자체는 도메인 단위 테스트가 망라하고, 여기서는 그것이 400 과 도메인
 * code 로 나가는지만 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminCuratedLinkIntegrationTest {

    private static final String URL = "/api/v1/admin/curated-links";
    private static final String ONE_URL = URL + "/{id}";
    private static final String REGION_URL = "/api/v1/regions/{regionId}";

    private static final String VALID_BODY =
            """
            { "title": "전남 관광포털", "chipText": "축제 보러 가기", "description": "이번 달 축제",
              "linkUrl": "https://tour.jeonnam.go.kr", "alwaysOn": true,
              "surfaces": ["REGION"], "displayOrder": 5, "published": true }""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CuratedLinkJpaRepository curatedLinkJpaRepository;

    @Autowired
    private AdminAccountJpaRepository adminAccountJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private UserIdentityRepository userIdentityRepository;

    // ── 누가 들어올 수 있나 ────────────────────────────────────────────────

    /**
     * <b>읽기까지 막는다.</b> 이 목록에는 아직 게시하지 않은 것과 기간이 지난 것이 전부 들어 있어, 팀 밖에
     * 보일 이유가 없다. Basic 은 사람이 Swagger 로 서버를 들여다보는 수단이지 백오피스 자격증명이 아니다.
     */
    @Test
    void 역할_없는_자격증명은_목록도_못_본다() throws Exception {
        mockMvc.perform(get(URL).with(basicOnly())).andExpect(status().isForbidden());
    }

    @Test
    void 일반_사용자_토큰으로는_못_들어온다() throws Exception {
        mockMvc.perform(get(URL).with(loginAs(UUID.randomUUID()))).andExpect(status().isForbidden());
        mockMvc.perform(post(URL)
                        .with(loginAs(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void 자격증명이_아예_없으면_401_이다() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void 어드민은_들어온다() throws Exception {
        mockMvc.perform(get(URL).with(loginAsAdmin(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.pageResponse.size").value(Paging.DEFAULT_SIZE));
    }

    // ── CRUD ─────────────────────────────────────────────────────────────

    @Test
    void 만들면_201_로_그_값을_돌려준다() throws Exception {
        mockMvc.perform(post(URL)
                        .with(loginAsAdmin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.title").value("전남 관광포털"))
                .andExpect(jsonPath("$.data.surfaces[0]").value("REGION"))
                .andExpect(jsonPath("$.data.published").value(true))
                // 앱 응답과 달리 판정에 쓰이는 값까지 내려야 어드민이 고칠 수 있다.
                .andExpect(jsonPath("$.data.alwaysOn").value(true))
                .andExpect(jsonPath("$.data.displayOrder").value(5));
    }

    /** 수정은 전체 교체다 — 안 보낸 값은 남지 않고 지워진다. */
    @Test
    void 수정하면_안_보낸_값은_지워진다() throws Exception {
        UUID admin = UUID.randomUUID();
        long id = create(admin);

        mockMvc.perform(patch(ONE_URL, id)
                        .with(loginAsAdmin(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "이름만 바꾼다", "chipText": "칩", "linkUrl": "https://tour.jeonnam.go.kr",
                                  "alwaysOn": true, "surfaces": ["HOME"], "published": false }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("이름만 바꾼다"))
                .andExpect(jsonPath("$.data.description", nullValue()))
                .andExpect(jsonPath("$.data.published").value(false))
                .andExpect(jsonPath("$.data.surfaces[0]").value("HOME"));
    }

    @Test
    void 지우면_200_에_data_는_null_이고_다시_열면_404_다() throws Exception {
        UUID admin = UUID.randomUUID();
        long id = create(admin);

        mockMvc.perform(delete(ONE_URL, id).with(loginAsAdmin(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", nullValue()));

        mockMvc.perform(get(ONE_URL, id).with(loginAsAdmin(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CURATION-006"));
    }

    /**
     * 없는 것을 지우라는 요청을 200 으로 넘기지 않는다. 어드민 화면은 목록을 들고 있어서, 다른 탭에서 이미
     * 지운 항목을 누르면 여기 닿는다 — 조용히 성공시키면 화면이 낡은 목록을 그대로 믿는다.
     */
    @Test
    void 없는_것을_지우면_404_다() throws Exception {
        mockMvc.perform(delete(ONE_URL, 987_654_321L).with(loginAsAdmin(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CURATION-006"));
    }

    // ── 검증 ─────────────────────────────────────────────────────────────

    /** 도메인이 막는 것은 400 + 도메인 code 로 나간다 — 어디서 걸렸는지 어드민이 알 수 있어야 한다. */
    @Test
    void https_아닌_주소는_400_에_도메인_code_다() throws Exception {
        mockMvc.perform(post(URL)
                        .with(loginAsAdmin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "제목", "chipText": "칩", "linkUrl": "http://tour.jeonnam.go.kr",
                                  "alwaysOn": true, "surfaces": ["HOME"] }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURATION-001"));
    }

    @Test
    void 상시가_아닌데_종료일이_없으면_400_이다() throws Exception {
        mockMvc.perform(post(URL)
                        .with(loginAsAdmin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "제목", "chipText": "칩", "linkUrl": "https://a.example",
                                  "alwaysOn": false, "surfaces": ["HOME"] }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURATION-002"));
    }

    /** 모양 검증(빈 값·길이)은 Bean Validation 이 먼저 잡는다 — 도메인까지 가지 않는다. */
    @Test
    void 노출_화면을_안_고르면_400_이다() throws Exception {
        mockMvc.perform(post(URL)
                        .with(loginAsAdmin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "제목", "chipText": "칩", "linkUrl": "https://a.example",
                                  "alwaysOn": true, "surfaces": [] }"""))
                .andExpect(status().isBadRequest());
    }

    // ── 감사 흔적 · 앱 반영 ────────────────────────────────────────────────

    /**
     * 배포 없이 값을 고칠 수 있게 되면 <b>누가 언제 바꿨는지가 유일한 추적 수단</b>이 된다. seed SQL
     * 시절에는 git blame 이 그 역할을 했다.
     */
    @Test
    void 고친_어드민의_이름이_남는다() throws Exception {
        UUID admin = whitelistedAdmin("박세빈");

        long id = create(admin);

        assertEquals("박세빈", curatedLinkJpaRepository.findById(id).orElseThrow().getUpdatedBy());
    }

    /** 화이트리스트에 이름이 없어도 쓰기를 막지 않는다 — 권한 판정은 이미 끝났고, 여기서 막으면 403 이 된다. */
    @Test
    void 이름을_못_찾아도_쓰기는_막지_않는다() throws Exception {
        long id = create(UUID.randomUUID());

        assertEquals(null, curatedLinkJpaRepository.findById(id).orElseThrow().getUpdatedBy());
    }

    /** 이 에픽의 목적 — 배포 없이 앱 화면이 바뀌는 것. */
    @Test
    void 어드민이_게시하면_앱_응답에_바로_실린다() throws Exception {
        UUID admin = UUID.randomUUID();
        long id = create(admin);

        mockMvc.perform(get(REGION_URL, 1).with(loginAs(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedLinks[*].title", hasItem("전남 관광포털")));

        mockMvc.perform(delete(ONE_URL, id).with(loginAsAdmin(admin))).andExpect(status().isOk());

        mockMvc.perform(get(REGION_URL, 1).with(loginAs(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curatedLinks[*].title", not(hasItem("전남 관광포털"))));
    }

    /** 어드민 목록은 <b>미공개도 보인다</b> — 안 보이면 켜러 갈 수가 없다. */
    @Test
    void 어드민_목록에는_미공개_항목도_보인다() throws Exception {
        UUID admin = UUID.randomUUID();
        curatedLinkJpaRepository.save(CuratedLink.builder()
                .title("작성 중인 배너")
                .chipText("아직")
                .linkUrl("https://a.example")
                .alwaysOn(true)
                .surfaces(Set.of(Surface.HOME))
                .published(false)
                .build());

        mockMvc.perform(get(URL).param("size", "100").with(loginAsAdmin(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].title", hasItem("작성 중인 배너")));
    }

    /** 상한이 없으면 {@code size=100000} 한 번으로 페이지네이션이 없던 때와 같아진다. */
    @Test
    void 페이지_크기는_상한으로_잘린다() throws Exception {
        mockMvc.perform(get(URL).param("size", "100000").with(loginAsAdmin(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageResponse.size").value(Paging.MAX_SIZE));
    }

    // ── fixture ──────────────────────────────────────────────────────────

    private long create(UUID admin) throws Exception {
        String body = mockMvc.perform(post(URL)
                        .with(loginAsAdmin(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    /**
     * 화이트리스트에 오른 어드민을 만든다 — 감사 흔적에 적을 이름은 <b>provider 신원을 거쳐</b> 찾는다.
     *
     * <p>화이트리스트의 키가 우리 사용자 id 가 아니라 provider 식별자라, 사용자·신원·화이트리스트 셋을
     * 모두 심어야 실제 경로가 돈다.
     */
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
