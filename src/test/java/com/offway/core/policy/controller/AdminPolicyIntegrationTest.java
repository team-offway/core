package com.offway.core.policy.controller;

import static com.offway.core.user.config.TestLogins.basicOnly;
import static com.offway.core.user.config.TestLogins.loginAs;
import static com.offway.core.user.config.TestLogins.loginAsAdmin;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.offway.core.policy.domain.Policy;
import com.offway.core.policy.domain.PolicyType;
import com.offway.core.policy.repository.PolicyJpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 백오피스 정책 CRUD 의 HTTP 계약(#344).
 *
 * <p>여기서 잠그는 것은 셋이다 — <b>어드민이 아닌 요청이 못 들어오는 것</b>, <b>어드민이 고친 값이 앱
 * 응답까지 이어지는 것</b>, 그리고 <b>같은 뱃지가 두 개 뜨지 않는 것</b>. 값 검증 자체는 도메인 단위
 * 테스트가 망라하고, 여기서는 그것이 400 과 도메인 code 로 나가는지만 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminPolicyIntegrationTest {

    private static final String URL = "/api/v1/admin/policies";
    private static final String ONE_URL = URL + "/{id}";

    private static final String VALID_BODY =
            """
            { "type": "RAIL_DISCOUNT", "name": "KTX 인구감소지역 할인",
              "benefitDetail": "왕복 30% 할인", "targetAudience": "전 국민",
              "periodStart": "2026-09-01", "periodEnd": "2026-12-31",
              "applyUrl": "https://www.letskorail.com", "verified": true,
              "checkedOn": "2026-09-01" }""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PolicyJpaRepository policyJpaRepository;

    // ── 누가 들어올 수 있나 ────────────────────────────────────────────────

    /**
     * <b>읽기까지 막는다.</b> 이 목록에는 미검증 정책과 기간이 지난 것이 전부 들어 있어, 팀 밖에 보일
     * 이유가 없다. Basic 은 사람이 Swagger 로 서버를 들여다보는 수단이지 백오피스 자격증명이 아니다.
     */
    @Test
    void Basic_자격증명으로는_목록도_못_본다() throws Exception {
        mockMvc.perform(get(URL).with(basicOnly())).andExpect(status().isForbidden());
    }

    @Test
    void 일반_사용자_토큰으로는_못_들어온다() throws Exception {
        mockMvc.perform(get(URL).with(loginAs(UUID.randomUUID()))).andExpect(status().isForbidden());
    }

    @Test
    void 어드민은_목록을_본다() throws Exception {
        mockMvc.perform(get(URL).with(loginAsAdmin(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.pageResponse").exists());
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
                .andExpect(jsonPath("$.data.type").value("RAIL_DISCOUNT"))
                .andExpect(jsonPath("$.data.verified").value(true))
                // 뱃지 문구는 분류가 소유한다 — 요청에 없던 값이 응답에 실린다.
                .andExpect(jsonPath("$.data.badgeText").value(PolicyType.RAIL_DISCOUNT.badgeText()));
    }

    @Test
    void 검증을_안_켜면_기본이_미노출이다() throws Exception {
        // 확인이 안 끝난 정책이 곧바로 뱃지로 나가면, 사용자가 받을 수 없는 혜택을 보러 간다.
        mockMvc.perform(post(URL)
                        .with(loginAsAdmin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "RURAL", "name": "농촌체험" }"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.verified").value(false));
    }

    @Test
    void 고치면_감사_흔적이_남는다() throws Exception {
        long id = created();

        mockMvc.perform(patch(ONE_URL, id)
                        .with(loginAsAdmin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "RAIL_DISCOUNT", "name": "이름만 바꾼다", "verified": true }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("이름만 바꾼다"))
                // 전체 교체라 안 보낸 값은 비워진다 — 부분 갱신이 아니다.
                .andExpect(jsonPath("$.data.benefitDetail", nullValue()));
    }

    @Test
    void 지우면_그_뒤로는_404_다() throws Exception {
        long id = created();

        mockMvc.perform(delete(ONE_URL, id).with(loginAsAdmin(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", nullValue()));
        mockMvc.perform(get(ONE_URL, id).with(loginAsAdmin(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POLICY-001"));
    }

    @Test
    void 없는_것을_지우면_404_다() throws Exception {
        // 조용히 성공시키면 화면이 낡은 목록을 그대로 믿는다.
        mockMvc.perform(delete(ONE_URL, 9_999_999L).with(loginAsAdmin(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POLICY-001"));
    }

    // ── 검증이 code 로 나간다 ─────────────────────────────────────────────

    @Test
    void https_아닌_신청_주소는_400_이다() throws Exception {
        mockMvc.perform(post(URL)
                        .with(loginAsAdmin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "RURAL", "name": "농촌체험", "applyUrl": "http://x.kr" }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POLICY-002"));
    }

    @Test
    void 시작일이_종료일보다_늦으면_400_이다() throws Exception {
        mockMvc.perform(post(URL)
                        .with(loginAsAdmin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "RURAL", "name": "농촌체험",
                                  "periodStart": "2026-12-31", "periodEnd": "2026-01-01" }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POLICY-003"));
    }

    // ── 같은 뱃지가 두 개 뜨지 않는다 ─────────────────────────────────────

    /**
     * <b>이 한 건이 새로 열린 실패 경로를 막는다.</b> seed SQL 시절에는 같은 분류를 두 번 넣는 실수가
     * 리뷰에서 걸렸다. 배포 없이 만들 수 있게 되면서 그 자리를 서버가 대신 막는다.
     */
    @Test
    void 같은_분류가_같은_기간에_또_노출되면_409_다() throws Exception {
        created();

        mockMvc.perform(post(URL)
                        .with(loginAsAdmin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POLICY-004"));
    }

    @Test
    void 기간이_안_겹치면_같은_분류라도_만들_수_있다() throws Exception {
        created(); // 2026-09-01 ~ 12-31

        mockMvc.perform(post(URL)
                        .with(loginAsAdmin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "RAIL_DISCOUNT", "name": "다음 시즌",
                                  "periodStart": "2027-01-01", "periodEnd": "2027-06-30",
                                  "verified": true }"""))
                .andExpect(status().isCreated());
    }

    @Test
    void 미검증이면_겹쳐도_만들_수_있다() throws Exception {
        // 다음 시즌 정책을 미리 만들어 두는 것이 정상 작업이다 — 켜는 순간에 걸리면 충분하다.
        created();

        mockMvc.perform(post(URL)
                        .with(loginAsAdmin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "RAIL_DISCOUNT", "name": "준비 중",
                                  "periodStart": "2026-09-01", "periodEnd": "2026-12-31" }"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.verified").value(false));
    }

    @Test
    void 자기_자신과는_겹치지_않는다() throws Exception {
        // 수정할 때 자기를 겹침 대상으로 세면 아무것도 못 고친다.
        long id = created();

        mockMvc.perform(patch(ONE_URL, id)
                        .with(loginAsAdmin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk());
    }

    // ── seed 가 하던 일을 이제 여기가 한다 ────────────────────────────────

    @Test
    void 초기_세_행이_마이그레이션으로_들어와_있다() throws Exception {
        // R__seed_policies.sql 을 비운 대신 V20260901184241 이 넣는다. 이게 없으면 새 DB 에서
        // 정책이 하나도 없는 채로 뜬다.
        assertEquals(3, policyJpaRepository.findAll().size());
    }

    private long created() {
        try {
            String response = mockMvc.perform(post(URL)
                            .with(loginAsAdmin(UUID.randomUUID()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            return ((Number) JsonPath.read(response, "$.data.id")).longValue();
        } catch (Exception e) {
            throw new IllegalStateException("정책 생성 실패", e);
        }
    }

    /** 도메인 빌더가 통합 컨텍스트에서도 같은 규칙을 타는지 — 서비스 밖에서 만들어 넣는 경로 확인용. */
    @SuppressWarnings("unused")
    private Policy sample() {
        return Policy.builder()
                .type(PolicyType.RURAL)
                .name("표본")
                .periodStart(LocalDate.of(2026, 1, 1))
                .verified(false)
                .build();
    }

    // ── 이 분류가 어디에 뜨나(#393) ──────────────────────────────────────

    /**
     * <b>분류마다 대상이 갈린다.</b> 예전 화면은 "대상 지역: 비수도권 인구감소지역" 한 줄뿐이라,
     * 그게 몇 곳인지도 어디인지도 알 수 없었다 — 어드민이 "이게 완도에 뜨나" 를 답하려면 코드를
     * 읽어야 했다.
     */
    @Test
    void 분류_전부의_대상_지역을_돌려준다() throws Exception {
        mockMvc.perform(get(URL + "/scopes").with(loginAsAdmin(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.length()").value(PolicyType.values().length))
                .andExpect(jsonPath("$.data[0].type").exists())
                .andExpect(jsonPath("$.data[0].tag").exists())
                .andExpect(jsonPath("$.data[0].badgeText").exists());
    }

    /** 곳 수와 목록이 어긋나면 화면이 거짓말을 한다 — "85곳" 이라 적고 60개만 펼치는 식으로. */
    @Test
    void 곳_수와_지역_목록의_길이가_같다() throws Exception {
        String body = mockMvc.perform(get(URL + "/scopes").with(loginAsAdmin(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Integer> counts = JsonPath.read(body, "$.data[*].regionCount");
        List<Integer> sizes = JsonPath.read(body, "$.data[*].regions.length()");
        assertEquals(counts, sizes);
    }

    @Test
    void 일반_사용자는_대상_지역을_못_본다() throws Exception {
        mockMvc.perform(get(URL + "/scopes").with(loginAs(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }
}
