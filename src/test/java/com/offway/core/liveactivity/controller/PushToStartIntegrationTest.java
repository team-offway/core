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

    /**
     * 같은 토큰이 다른 소유자로 와도 <b>앞 사람의 등록을 뺏어오지 않는다</b>.
     *
     * <p>한 기기에 두 계정이 로그인하는 경우다. 토큰 단독 유니크면 주인이 갈아끼워져, 남의 토큰을
     * 아는 쪽이 그 사람의 카드를 가로챌 수 있다.
     */
    @Test
    void 같은_토큰이_다른_소유자로_오면_행이_따로_생긴다() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        String shared = uniqueToken();

        register(first, shared);
        register(second, shared);

        assertEquals(1, rowsOf(first).size(), "앞 사람의 등록이 사라졌다");
        assertEquals(1, rowsOf(second).size());
    }

    @Test
    void 토큰이_비면_400이다() throws Exception {
        mockMvc.perform(put(URL).with(loginAs(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
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

    private static String uniqueToken() {
        return "pts-" + UUID.randomUUID();
    }
}
