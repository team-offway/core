package com.offway.core.liveactivity.controller;

import static com.offway.core.user.config.TestLogins.loginAs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.liveactivity.domain.PushToStartToken;
import com.offway.core.liveactivity.repository.PushToStartTokenRepository;
import com.offway.core.user.config.WithLoginUser;
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
 * push-to-start 토큰 등록·해제의 HTTP 계약(#583).
 *
 * <p>여기서 지키는 것 둘.
 *
 * <ul>
 *   <li><b>멱등</b> — 앱은 시작할 때마다 같은 토큰을 보낸다. 행이 늘면 같은 기기에 카드가 둘 뜬다
 *   <li><b>코스를 묻지 않는다</b> — 이 토큰은 기기 단위라 어느 여행과도 안 묶인다. 무엇을 띄울지는
 *       배치가 그날 정한다
 * </ul>
 *
 * <p>클래스 레벨 {@code @Transactional} 로 롤백한다 — 여기서 남긴 등록은 소유자를 안 가리고 전부
 * 훑는 정오 배치 테스트의 대상에 섞인다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithLoginUser
@Transactional
class PushToStartIntegrationTest {

    private static final String URL = "/api/v1/live-activities/push-to-start";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PushToStartTokenRepository pushToStartTokenRepository;

    @Test
    void 등록하면_200과_빈_data를_준다() throws Exception {
        UUID owner = UUID.randomUUID();
        String token = uniqueToken();

        mockMvc.perform(put(URL).with(loginAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").doesNotExist());

        List<PushToStartToken> rows = rowsOf(owner);
        assertEquals(1, rows.size());
        assertEquals(token, rows.getFirst().getToken());
    }

    /** 앱은 시작할 때마다 보낸다 — 몇 번을 보내도 결과가 같아야 한다. */
    @Test
    void 같은_토큰을_다시_보내도_행이_늘지_않는다() throws Exception {
        UUID owner = UUID.randomUUID();
        String token = uniqueToken();

        register(owner, token);
        register(owner, token);
        register(owner, token);

        assertEquals(1, rowsOf(owner).size(), "행이 늘었다 — 같은 기기에 카드가 둘 뜬다");
    }

    /** 폰과 태블릿이면 행도 둘이다 — 둘 다에 카드를 띄워야 한다. */
    @Test
    void 기기가_다르면_행이_따로_생긴다() throws Exception {
        UUID owner = UUID.randomUUID();

        register(owner, uniqueToken());
        register(owner, uniqueToken());

        assertEquals(2, rowsOf(owner).size());
    }

    @Test
    void 토큰이_비면_400이다() throws Exception {
        mockMvc.perform(put(URL).with(loginAs(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    /**
     * hex 가 아니면 <b>입구에서</b> 막는다.
     *
     * <p>이 값은 그대로 APNs 요청 URL 에 붙는다. 공백이 섞이면 {@code URI.create} 가 터지고 그 실패는
     * {@code FAILED} 로 번역되는데, <b>{@code GONE} 이 아니라 행이 안 지워져 매일 같은 실패를
     * 되풀이한다.</b> 여기서 한 번 막는 것이 그 반복을 없애는 유일한 자리다.
     */
    @Test
    void hex가_아니면_400이다() throws Exception {
        mockMvc.perform(put(URL).with(loginAs(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("pts-not-hex")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    /** 길이가 홀수면 바이트로 안 떨어진다 — 앱이 {@code %02x} 로 만든 값일 수 없다. */
    @Test
    void 길이가_홀수면_400이다() throws Exception {
        mockMvc.perform(put(URL).with(loginAs(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("80a1b")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    /**
     * <b>같은 기기에 다른 계정이 로그인하면 앞 계정의 등록이 사라진다.</b>
     *
     * <p>앞사람이 로그아웃을 안 하고 계정을 바꾸면 그 행이 남는데, 그러면 배치가 <b>앞사람의 여행지·
     * 날짜를 지금 이 기기 잠금화면에 그린다</b> — 남의 일정이 남의 화면에 뜨는 것이라 단순한 찌꺼기가
     * 아니다. 앱이 해제를 안 불러도 여기서 끊긴다.
     */
    @Test
    void 같은_기기에_다른_계정이_등록하면_앞_계정은_사라진다() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        String device = uniqueToken();

        register(first, device);
        register(second, device);

        assertTrue(rowsOf(first).isEmpty(), "앞 계정의 등록이 남았다 — 그 사람 일정이 이 기기에 뜬다");
        assertEquals(1, rowsOf(second).size());
    }

    /** 토큰을 실으면 <b>그 기기만</b> 해제된다 — 로그아웃(#389)이 갈리는 기준과 같다. */
    @Test
    void 토큰을_실으면_그_기기만_해제된다() throws Exception {
        UUID owner = UUID.randomUUID();
        String phone = uniqueToken();
        String tablet = uniqueToken();
        register(owner, phone);
        register(owner, tablet);

        mockMvc.perform(delete(URL).with(loginAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(phone)))
                .andExpect(status().isOk());

        List<PushToStartToken> rows = rowsOf(owner);
        assertEquals(1, rows.size(), "폰에서 로그아웃했는데 태블릿 카드까지 끊겼다");
        assertEquals(tablet, rows.getFirst().getToken());
    }

    @Test
    void 해제하면_그_사람의_등록이_전부_사라진다() throws Exception {
        UUID owner = UUID.randomUUID();
        register(owner, uniqueToken());
        register(owner, uniqueToken());

        mockMvc.perform(delete(URL).with(loginAs(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertTrue(rowsOf(owner).isEmpty());
    }

    /** 지울 것이 없어도 성공이다 — 로그아웃 화면이 404 를 띄울 이유가 없다. */
    @Test
    void 지울_등록이_없어도_200이다() throws Exception {
        mockMvc.perform(delete(URL).with(loginAs(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void 해제해도_남의_등록은_그대로다() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        register(owner, uniqueToken());

        mockMvc.perform(delete(URL).with(loginAs(stranger)))
                .andExpect(status().isOk());

        assertEquals(1, rowsOf(owner).size(), "남이 부른 해제로 내 등록이 사라졌다");
    }

    private void register(UUID owner, String token) throws Exception {
        mockMvc.perform(put(URL).with(loginAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(token)))
                .andExpect(status().isOk());
    }

    private List<PushToStartToken> rowsOf(UUID owner) {
        return pushToStartTokenRepository.findAll().stream()
                .filter(row -> owner.equals(row.getUserId()))
                .toList();
    }

    private static String body(String token) {
        return "{\"token\": \"%s\"}".formatted(token);
    }

    /** 앱이 보내는 모양 그대로 — {@code Data} 를 바이트마다 {@code %02x} 로 푼 hex 문자열이다. */
    private static String uniqueToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
