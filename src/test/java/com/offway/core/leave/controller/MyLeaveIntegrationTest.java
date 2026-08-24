package com.offway.core.leave.controller;

import static com.offway.core.user.config.TestLogins.loginAs;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.offway.core.user.config.WithLoginUser;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * "내 연차" 통합 테스트.
 *
 * <p><b>주인은 요청이 아니라 인증이 정한다</b>(#280). 예전에는 {@code X-Guest-Id} 헤더가 소유 키라 테스트마다
 * 다른 문자열을 붙였는데, 이제는 {@code @WithLoginUser} 가 넣은 access 토큰 principal(UUID)이 주인이다 —
 * 그래서 어느 요청에도 소유 키 헤더가 없다.
 *
 * <p><b>주인은 어디에도 고정하지 않는다.</b> 값을 비운 {@code @WithLoginUser} 는 테스트마다 새 UUID 이고,
 * 그것으로 부족한 시나리오(옛 데이터를 SQL 로 심는 것·소유자 격리)는 <b>본문에서</b> {@code UUID.randomUUID()}
 * 로 주인을 만들어 요청마다 실어 보낸다.
 *
 * <p>이 클래스는 DB 를 롤백하지 않는다(공유 컨텍스트). 그래서 고정 UUID 를 쓰면 <b>같은 DB 로 두 번째로
 * 돌릴 때</b> 앞 실행의 행이 남아 "내역이 하나뿐"·"음수 행 2건" 같은 전제가 깨진다. 지금은 Testcontainers 가
 * 실행마다 새 MySQL 을 띄워 드러나지 않지만, 원인을 남겨 둘 이유가 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MyLeaveIntegrationTest {

    private static final String URL = "/api/v1/leaves/me";
    private static final String USAGES_URL = URL + "/usages";

    /** {@code SecurityConfig} 가 소유 데이터 경로에 요구하는 권한 — {@code WithLoginUser} 와 같은 값이다. */
    private static final String USER_AUTHORITY = "ROLE_USER";

    @Autowired
    private MockMvc mockMvc;

    /** 도메인이 막는 값을 옛 데이터로 심을 때만 쓴다 — 그 밖의 준비는 전부 API 로 한다. */
    @Autowired
    private JdbcTemplate jdbcTemplate;


    private ResultActions addUsage(String body) throws Exception {
        return mockMvc.perform(
                post(USAGES_URL).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions setTotalDays(double totalDays) throws Exception {
        return mockMvc.perform(patch(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"totalDays\": " + totalDays + "}"));
    }

    /**
     * 주인을 본문에서 만든 시나리오용 오버로드 — 요청마다 그 주인을 실어 보낸다.
     *
     * <p>{@code @WithLoginUser} 는 어노테이션이라 값이 컴파일 상수여야 해서, 본문에서 만든 UUID 를 쓸 수 없다.
     */
    private ResultActions addUsage(RequestPostProcessor login, String body) throws Exception {
        return mockMvc.perform(post(USAGES_URL)
                .with(login)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions setTotalDays(RequestPostProcessor login, double totalDays) throws Exception {
        return mockMvc.perform(patch(URL)
                .with(login)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"totalDays\": " + totalDays + "}"));
    }

    /** 방금 만든 내역의 ID — 삭제 대상이다. 이 소유자에게 내역이 하나뿐인 시나리오에서만 쓴다. */
    private static long onlyUsageId(String responseBody) {
        return ((Number) JsonPath.read(responseBody, "$.data.usages[0].id")).longValue();
    }

    @Test
    @WithLoginUser
    void 설정한_적_없으면_총0_내역없음으로_내려준다() throws Exception {
        // 없는 소유자를 404 로 돌려주면 클라이언트가 "처음 쓰는 사람" 을 예외로 다뤄야 한다.
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.totalDays").value(0.0))
                .andExpect(jsonPath("$.data.usedDays").value(0.0))
                .andExpect(jsonPath("$.data.remainingDays").value(0.0))
                .andExpect(jsonPath("$.data.usages.length()").value(0));
    }

    @Test
    @WithLoginUser
    void 총_연차를_수정하면_남은_연차가_따라_바뀐다() throws Exception {
        setTotalDays(15)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDays").value(15.0))
                .andExpect(jsonPath("$.data.remainingDays").value(15.0));

        // 다시 수정 — 새 행이 생기지 않고 같은 소유자의 값이 바뀐다
        setTotalDays(12.5)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDays").value(12.5))
                .andExpect(jsonPath("$.data.remainingDays").value(12.5));
    }

    @Test
    @WithLoginUser
    void 사용내역을_쌓으면_남은_연차가_줄고_지우면_되돌아온다() throws Exception {
        setTotalDays(10).andExpect(status().isOk());

        String created = addUsage("{\"usedOn\": \"2026-05-08\", \"days\": 3, \"reason\": \"제주 여행\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.usedDays").value(3.0))
                .andExpect(jsonPath("$.data.remainingDays").value(7.0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long usageId = onlyUsageId(created);

        // 취소는 음수 상쇄가 아니라 그 행을 지우는 것이다(#265) — 응답은 갱신된 내 연차 전체다.
        mockMvc.perform(delete(USAGES_URL + "/{id}", usageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.totalDays").value(10.0))
                .andExpect(jsonPath("$.data.usedDays").value(0.0))
                .andExpect(jsonPath("$.data.remainingDays").value(10.0))
                .andExpect(jsonPath("$.data.usages.length()").value(0));
    }

    /**
     * 이 PR 의 존재 이유 — 같은 취소가 두 번 들어와도 <b>잔여가 총 연차를 넘지 않는다</b>.
     *
     * <p>예전에는 취소를 음수 등록으로 흉내냈고, 재시도·중복 탭으로 두 번 들어오면 총 15일인 사람의 잔여가
     * 17 이 됐다. 지금은 두 번째 삭제가 404 로 끊기고 잔여는 총 그대로다.
     */
    @Test
    @WithLoginUser
    void 취소를_두_번_보내도_잔여가_총_연차를_넘지_않는다() throws Exception {
        setTotalDays(15).andExpect(status().isOk());
        String created = addUsage("{\"usedOn\": \"2026-05-08\", \"days\": 2}")
                .andExpect(jsonPath("$.data.remainingDays").value(13.0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long usageId = onlyUsageId(created);

        mockMvc.perform(delete(USAGES_URL + "/{id}", usageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remainingDays").value(15.0));

        mockMvc.perform(delete(USAGES_URL + "/{id}", usageId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEAVE-012"));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedDays").value(0.0))
                .andExpect(jsonPath("$.data.remainingDays").value(15.0));
    }

    @Test
    @WithLoginUser
    void 음수_등록은_400_LEAVE_013_으로_거절하고_삭제를_안내한다() throws Exception {
        // 상쇄 등록이 잔여를 총보다 크게 만들던 자리다. 단위 위반(LEAVE-010)과 코드를 가른다.
        addUsage("{\"usedOn\": \"2026-05-09\", \"days\": -1, \"reason\": \"하루 취소\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("LEAVE-013"))
                .andExpect(jsonPath("$.detail").value("연차 사용은 0.25일 단위의 양수여야 합니다. 되돌리려면 해당 내역을 삭제해 주세요."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ── 내역 수정(#267) ────────────────────────────────────────

    @Test
    @WithLoginUser
    void 내역을_고치면_잔여가_따라_바뀐다() throws Exception {
        mockMvc.perform(patch(URL)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalDays\": 10}"))
                .andExpect(status().isOk());
        long usageId = onlyUsageId(addUsage("{\"usedOn\": \"2026-05-08\", \"days\": 3, \"reason\": \"제주\"}")
                .andExpect(jsonPath("$.data.remainingDays").value(7.0))
                .andReturn().getResponse().getContentAsString());

        // 응답은 삭제와 같은 모양이다 — 화면이 한 번의 왕복으로 다시 그린다.
        mockMvc.perform(patchUsage(usageId, "{\"days\": 1.25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.usedDays").value(1.25))
                .andExpect(jsonPath("$.data.remainingDays").value(8.75))
                .andExpect(jsonPath("$.data.usages.length()").value(1))
                // 안 보낸 필드는 그대로다.
                .andExpect(jsonPath("$.data.usages[0].usedOn").value("2026-05-08"))
                .andExpect(jsonPath("$.data.usages[0].reason").value("제주"));
    }

    @Test
    @WithLoginUser
    void 빈_사유를_보내면_사유가_지워진다() throws Exception {
        // 안 보냄·null 은 "그대로 두라" 라, 지우는 신호는 빈 문자열뿐이다.
        long usageId = onlyUsageId(addUsage("{\"usedOn\": \"2026-05-08\", \"days\": 1, \"reason\": \"제주\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(patchUsage(usageId, "{\"reason\": \"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usages[0].reason").doesNotExist())
                .andExpect(jsonPath("$.data.usages[0].days").value(1.0));
    }

    @Test
    @WithLoginUser
    void 수정도_등록과_같은_400_을_준다() throws Exception {
        long usageId = onlyUsageId(addUsage("{\"usedOn\": \"2026-05-08\", \"days\": 1}")
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(patchUsage(usageId, "{\"days\": 0.3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-010"));
        // 음수는 사유를 갈라 답한다 — 화면이 "삭제로 취소하세요" 를 안내해야 한다(#276).
        mockMvc.perform(patchUsage(usageId, "{\"days\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-013"));
    }

    @Test
    @WithLoginUser
    void 없는_내역을_고치면_404_LEAVE_012() throws Exception {
        mockMvc.perform(patchUsage(987654321L, "{\"days\": 1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEAVE-012"));
    }

    @Test
    @WithLoginUser
    void 남의_내역은_고칠_수_없고_없는_것과_같은_404다() throws Exception {
        // 헤더를 바꿔 남을 흉내내던 시나리오가 이제 성립하지 않는다(#280) — 실제로 다른 사용자로 로그인한다.
        // 그게 이 전환의 요지다: 소유자를 요청이 정하지 못한다.
        long usageId = onlyUsageId(mockMvc
                .perform(post(USAGES_URL).with(loginAs(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usedOn\": \"2026-05-08\", \"days\": 1}"))
                .andReturn().getResponse().getContentAsString());

        // 만든 사람과 다른 사용자로 고치려 든다 — 없는 것과 같은 404 여야 한다(존재를 흘리지 않는다).
        mockMvc.perform(patchUsage(usageId, "{\"days\": 2}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEAVE-012"));
    }

    private org.springframework.test.web.servlet.RequestBuilder patchUsage(
            long usageId, String body) {
        return patch(USAGES_URL + "/{id}", usageId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    @Test
    @WithLoginUser
    void 없는_내역을_지우면_404_LEAVE_012() throws Exception {
        mockMvc.perform(delete(USAGES_URL + "/{id}", 987654321L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("LEAVE-012"))
                .andExpect(jsonPath("$.detail").value("연차 사용 내역을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 남의_내역은_지울_수_없고_없는_것과_같은_404다() throws Exception {
        // 주인을 본문에서 만든다 — onlyUsageId 가 "이 사람에게 내역이 하나뿐" 을 전제하는데, 고정 UUID 면
        // 같은 DB 를 재사용해 두 번째로 돌릴 때 앞 실행의 행이 남아 그 전제가 깨진다.
        UUID owner = UUID.randomUUID();
        RequestPostProcessor login = loginAs(owner);

        // 403 으로 나눠 답하면 id 를 넣어보며 "이 번호는 있다" 를 알아낼 수 있다 — 코스 조회와 같은 규칙이다.
        String created = addUsage(login, "{\"usedOn\": \"2026-05-08\", \"days\": 1}")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long usageId = onlyUsageId(created);

        // 로그인은 했지만 다른 사람이다 — 이제 이 "다른 사람" 은 스스로 고를 수 없는 값(토큰의 주체)이다.
        mockMvc.perform(delete(USAGES_URL + "/{id}", usageId).with(loginAs(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEAVE-012"));

        // 주인의 내역은 그대로 남아 있다.
        mockMvc.perform(get(URL).with(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usages.length()").value(1));
    }

    /**
     * 이 PR 이 손대지 않기로 한 <b>이미 쌓인 음수 행</b>이 실제로 어떻게 보이고 어떻게 정리되는가(#265).
     *
     * <p>세 가지를 한 번에 잠근다. ① 원장 합이 음수여도 잔여가 총을 넘지 않는다(clamp 가 실데이터에서 돈다)
     * ② 그래도 <b>목록에는 음수 행이 그대로 보인다</b> — 마이그레이션으로 지우지 않기로 했으므로 이건 사양이다
     * ③ 그 행을 사용자가 직접 지울 수 있고, 지우면 장부가 실제로 맞아떨어진다.
     *
     * <p>API 로는 더 이상 음수를 넣을 수 없으므로 도메인을 우회해 직접 적재한다 — 그게 옛 데이터의 실제 모습이다
     * (하이드레이션은 생성자를 거치지 않는다).
     */
    @Test
    void 이미_쌓인_음수_행은_목록에_보이고_사용자가_지워_정리할_수_있다() throws Exception {
        // 심을 때와 읽을 때가 같은 주인이면 되고, 그 주인이 <b>이 실행에만</b> 속하면 된다. 고정 UUID 로 두면
        // 같은 DB 를 재사용해 두 번째로 돌릴 때 앞 실행의 음수 행이 남아 "3건" 단언이 깨진다.
        UUID owner = UUID.randomUUID();
        RequestPostProcessor login = loginAs(owner);

        setTotalDays(login, 15).andExpect(status().isOk());
        addUsage(login, "{\"usedOn\": \"2026-05-08\", \"days\": 2}").andExpect(status().isCreated());
        // 삭제 API 가 없던 시절의 상쇄 등록 — 같은 취소가 두 번 들어와 원장 합이 -2 가 된 그 장부다.
        insertLegacyReversal(owner, -2.0);
        insertLegacyReversal(owner, -2.0);

        String found = mockMvc.perform(get(URL).with(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedDays").value(0.0))
                .andExpect(jsonPath("$.data.remainingDays").value(15.0)) // 17 이 아니다
                .andExpect(jsonPath("$.data.usages.length()").value(3))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<Integer> negativeIds = JsonPath.read(found, "$.data.usages[?(@.days < 0)].id");
        assertEquals(2, negativeIds.size(), "음수 행은 감춰지지 않고 목록에 그대로 나간다");

        for (Integer id : negativeIds) {
            mockMvc.perform(delete(USAGES_URL + "/{id}", id.longValue()).with(login))
                    .andExpect(status().isOk());
        }

        // 정리하고 나면 clamp 가 가리고 있던 값과 실제 장부가 같아진다.
        mockMvc.perform(get(URL).with(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedDays").value(2.0))
                .andExpect(jsonPath("$.data.remainingDays").value(13.0))
                .andExpect(jsonPath("$.data.usages.length()").value(1));
    }

    /** 도메인을 우회해 음수 행을 심는다 — 이제 팩토리가 막으므로 옛 데이터는 이 길로만 재현된다. */
    private void insertLegacyReversal(UUID owner, double days) {
        jdbcTemplate.update(
                "INSERT INTO leave_usage (user_id, used_on, days, reason) VALUES (?, ?, ?, ?)",
                toBinary(owner), LocalDate.of(2026, 5, 9), days, "하루 취소");
    }

    /** {@code user_id} 는 {@code BINARY(16)} 이다 — Hibernate 가 UUID 를 넣는 것과 같은 big-endian 바이트다. */
    private static byte[] toBinary(UUID userId) {
        return ByteBuffer.allocate(Long.BYTES * 2)
                .putLong(userId.getMostSignificantBits())
                .putLong(userId.getLeastSignificantBits())
                .array();
    }

    @Test
    @WithLoginUser
    void 반차는_0점5로_센다() throws Exception {
        setTotalDays(5).andExpect(status().isOk());

        addUsage("{\"usedOn\": \"2026-05-07\", \"days\": 1.5}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.remainingDays").value(3.5));
    }

    @Test
    @WithLoginUser
    void 남은_연차가_모자라도_막지_않고_음수로_내려준다() throws Exception {
        // 결정 #38 — 서버는 막지 않는다. 프론트가 경고하고 사용자가 확인하면 진행한다.
        setTotalDays(2).andExpect(status().isOk());

        addUsage("{\"usedOn\": \"2026-05-08\", \"days\": 5}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.remainingDays").value(-3.0));
    }

    /**
     * 소유자가 다르면 서로의 연차가 보이지 않는다 — 이 전환의 핵심이다(#280).
     *
     * <p>예전에는 이 격리가 "헤더 문자열을 모른다" 에 기대고 있었다. 그 값은 클라이언트가 정하는 것이라
     * 알아내면 그만이었다. 이제 주인은 access 토큰이 정하므로 다른 사용자는 남의 연차에 닿을 길이 없다.
     */
    @Test
    void 다른_사용자에게는_내_연차가_보이지_않는다() throws Exception {
        // 두 주인이 서로 다르기만 하면 되는 시나리오라 둘 다 본문에서 만든다.
        RequestPostProcessor mine = loginAs(UUID.randomUUID());

        setTotalDays(mine, 20).andExpect(status().isOk());

        mockMvc.perform(get(URL).with(loginAs(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDays").value(0.0))
                .andExpect(jsonPath("$.data.usages.length()").value(0));

        // 내 값은 그대로다 — 남이 조회했다고 달라지지 않는다.
        mockMvc.perform(get(URL).with(mine))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDays").value(20.0));
    }

    @Test
    @WithLoginUser
    void 반반차는_0점25로_센다() throws Exception {
        // 시안의 0.25 칩이 여기로 온다(#278). 앱은 이미 붙여뒀고, 서버가 막으면 그 칩을 고르는 순간
        // 400 문구가 사용자에게 그대로 보인다.
        setTotalDays(5).andExpect(status().isOk());

        addUsage("{\"usedOn\": \"2026-05-07\", \"days\": 1.25}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.remainingDays").value(3.75));
    }

    @Test
    @WithLoginUser
    void 반반차로_쌓은_잔여에_총량을_맞출_수_있다() throws Exception {
        // **사용만 열면 잔여가 안 맞는다.** 0.25 씩 세 번 쓰면 잔여가 14.25 인데, 총량이 0.5 격자면
        // 사용자가 그 값으로 장부를 정리할 수 없다 — 두 곳이 같은 격자를 써야 하는 이유다.
        setTotalDays(15).andExpect(status().isOk());
        for (int day = 11; day <= 13; day++) {
            addUsage("{\"usedOn\": \"2026-05-%d\", \"days\": 0.25}".formatted(day))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get(URL)).andExpect(status().isOk()).andExpect(jsonPath("$.data.remainingDays").value(14.25));

        setTotalDays(14.25).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalDays").value(14.25));
    }

    @Test
    @WithLoginUser
    void 총_연차가_0점25_단위가_아니면_400_LEAVE_009() throws Exception {
        setTotalDays(1.3)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-009"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @WithLoginUser
    void 총_연차가_음수면_400_LEAVE_009() throws Exception {
        setTotalDays(-1).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LEAVE-009"));
    }

    @Test
    @WithLoginUser
    void 총_연차가_상한을_넘으면_400_LEAVE_009() throws Exception {
        // 화면이 "최대 99일까지" 라고 안내한다 — 서버가 더 넉넉하면 화면을 안 거친 요청만 다른 규칙을 탄다(#142).
        setTotalDays(100).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LEAVE-009"));
    }

    @Test
    @WithLoginUser
    void 총_연차_경계값_0과_99는_받는다() throws Exception {
        // 화면 문구가 "0일보다 적게" · "최대 99일까지" 라 양끝은 유효하다.
        setTotalDays(0).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalDays").value(0.0));

        setTotalDays(99).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalDays").value(99.0));
    }

    @Test
    @WithLoginUser
    void 사용_증감이_0이면_400_LEAVE_010() throws Exception {
        addUsage("{\"usedOn\": \"2026-05-08\", \"days\": 0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-010"));
    }

    /**
     * 인증 없이 부르면 <b>네 엔드포인트 모두 401</b> 이다(#280).
     *
     * <p>예전에는 이 자리에 "소유 키 헤더가 없거나 비었으면 400" 이 있었다. 소유 키가 요청 헤더라 클라이언트가
     * 형식을 틀릴 수 있었기 때문이다. 이제 주인은 access 토큰이 정하므로 <b>틀릴 형식 자체가 없고</b>, 주체가
     * 없는 요청은 컨트롤러에 닿기 전에 인증에서 끊긴다. 네 곳이 같은 계약을 쓰는지 함께 확인한다.
     */
    @Test
    void 인증_없이_부르면_네_엔드포인트_모두_401_COMMON_401() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("COMMON-401"))
                .andExpect(jsonPath("$.detail").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content("{\"totalDays\": 10}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-401"));

        mockMvc.perform(post(USAGES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usedOn\": \"2026-05-08\", \"days\": 1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-401"));

        mockMvc.perform(delete(USAGES_URL + "/{id}", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-401"));
    }

    /**
     * 자격증명은 있는데 <b>주체가 없는</b> 요청은 403 으로 끊긴다 — 조용히 빈 연차를 내려주지 않는다.
     *
     * <p>#122 의 Basic 게이트로 들어온 요청이 이 모양이다(인증은 됐지만 {@code ROLE_USER} 가 없고 principal 이
     * UUID 가 아니다). 통과시키면 {@code @LoginUser} 가 null 로 풀려 주인 없는 조회가 "총 0 · 내역 없음 200" 으로
     * 나가는데, 그건 실패가 성공처럼 보이는 응답이다. {@code SecurityConfig} 가 그래서 소유 데이터 경로를
     * Bearer 전용으로 닫았다(#280).
     */
    @Test
    @WithMockUser(roles = {})
    void 권한_없는_자격증명으로는_403이고_빈_연차가_새지_않는다() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("COMMON-403"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    /**
     * 같은 소유자로 동시에 수정하면 <b>둘 다 성공</b>해야 한다.
     *
     * <p>잔액이 없는 상태에서 동시에 들어오면 둘 다 "없음" 을 보고 각자 만들려 든다. 한쪽은 유니크 제약에 걸리는데,
     * 그건 실패가 아니라 "먼저 넣은 쪽이 이겼다" 는 뜻이라 500 이 나가면 안 된다.
     *
     * <p><b>경합 구간이 좁아 매번 재현되지는 않는다.</b> 그래도 두는 이유는 이 시나리오가 500 을 내면 안 된다는
     * 계약을 코드에 남기기 위해서다 — 재현되는 날엔 잡는다.
     */
    @Test
    @WithLoginUser
    void 같은_소유자로_동시에_수정해도_둘_다_성공한다() throws Exception {
        // 풀 스레드는 SecurityContextHolder(ThreadLocal)를 상속받지 못한다 — 인증을 요청에 직접 싣는다.
        UUID owner = UUID.randomUUID();
        int threads = 2;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                double total = 10 + i; // 서로 다른 값 — 어느 쪽이 이겨도 둘 다 200 이어야 한다
                pool.submit(() -> {
                    try {
                        start.await();
                        statuses.add(mockMvc.perform(patch(URL)
                                        .with(loginAs(owner))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"totalDays\": " + total + "}"))
                                .andReturn().getResponse().getStatus());
                    } catch (Exception e) {
                        statuses.add(-1);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(20, TimeUnit.SECONDS), "동시 요청이 끝나야 한다");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(List.of(200, 200), statuses.stream().sorted().toList(),
                "동시 생성은 경합일 뿐 실패가 아니다 — 500 이 섞이면 안 된다. 실제=" + statuses);

        // 어느 쪽이 이겼든 값은 둘 중 하나여야 하고, 행이 두 개 생기지도 않는다.
        mockMvc.perform(get(URL).with(loginAs(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDays").value(anyOf(is(10.0), is(11.0))));
    }
}
