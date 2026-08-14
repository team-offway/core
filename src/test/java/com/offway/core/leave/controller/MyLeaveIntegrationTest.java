package com.offway.core.leave.controller;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * "내 연차" 통합 테스트.
 *
 * <p>소유자를 테스트마다 다르게 쓴다 — 이 클래스는 DB 를 롤백하지 않아(공유 컨텍스트) 같은 키를 쓰면 앞 테스트의
 * 잔여 상태가 다음 시나리오로 새어 든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class MyLeaveIntegrationTest {

    private static final String URL = "/api/v1/leaves/me";
    private static final String USAGES_URL = URL + "/usages";
    private static final String GUEST_HEADER = "X-Guest-Id";

    @Autowired
    private MockMvc mockMvc;

    private ResultActions addUsage(String guest, String body) throws Exception {
        return mockMvc.perform(post(USAGES_URL).header(GUEST_HEADER, guest)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** 방금 만든 내역의 ID — 삭제 대상이다. 이 소유자에게 내역이 하나뿐인 시나리오에서만 쓴다. */
    private static long onlyUsageId(String responseBody) {
        return ((Number) JsonPath.read(responseBody, "$.data.usages[0].id")).longValue();
    }

    @Test
    void 설정한_적_없으면_총0_내역없음으로_내려준다() throws Exception {
        // 없는 소유자를 404 로 돌려주면 클라이언트가 "처음 쓰는 사람" 을 예외로 다뤄야 한다.
        mockMvc.perform(get(URL).header(GUEST_HEADER, "leave-fresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.totalDays").value(0.0))
                .andExpect(jsonPath("$.data.usedDays").value(0.0))
                .andExpect(jsonPath("$.data.remainingDays").value(0.0))
                .andExpect(jsonPath("$.data.usages.length()").value(0));
    }

    @Test
    void 총_연차를_수정하면_남은_연차가_따라_바뀐다() throws Exception {
        String guest = "leave-update";

        mockMvc.perform(patch(URL).header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalDays\": 15}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDays").value(15.0))
                .andExpect(jsonPath("$.data.remainingDays").value(15.0));

        // 다시 수정 — 새 행이 생기지 않고 같은 소유자의 값이 바뀐다
        mockMvc.perform(patch(URL).header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalDays\": 12.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDays").value(12.5))
                .andExpect(jsonPath("$.data.remainingDays").value(12.5));
    }

    @Test
    void 사용내역을_쌓으면_남은_연차가_줄고_지우면_되돌아온다() throws Exception {
        String guest = "leave-usage";
        mockMvc.perform(patch(URL).header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalDays\": 10}"))
                .andExpect(status().isOk());

        String created = addUsage(guest, "{\"usedOn\": \"2026-05-08\", \"days\": 3, \"reason\": \"제주 여행\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.usedDays").value(3.0))
                .andExpect(jsonPath("$.data.remainingDays").value(7.0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long usageId = onlyUsageId(created);

        // 취소는 음수 상쇄가 아니라 그 행을 지우는 것이다(#265) — 응답은 갱신된 내 연차 전체다.
        mockMvc.perform(delete(USAGES_URL + "/{id}", usageId).header(GUEST_HEADER, guest))
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
    void 취소를_두_번_보내도_잔여가_총_연차를_넘지_않는다() throws Exception {
        String guest = "leave-double-cancel";
        mockMvc.perform(patch(URL).header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalDays\": 15}"))
                .andExpect(status().isOk());
        String created = addUsage(guest, "{\"usedOn\": \"2026-05-08\", \"days\": 2}")
                .andExpect(jsonPath("$.data.remainingDays").value(13.0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long usageId = onlyUsageId(created);

        mockMvc.perform(delete(USAGES_URL + "/{id}", usageId).header(GUEST_HEADER, guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remainingDays").value(15.0));

        mockMvc.perform(delete(USAGES_URL + "/{id}", usageId).header(GUEST_HEADER, guest))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEAVE-012"));

        mockMvc.perform(get(URL).header(GUEST_HEADER, guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedDays").value(0.0))
                .andExpect(jsonPath("$.data.remainingDays").value(15.0));
    }

    @Test
    void 음수_등록은_아직_받는다() throws Exception {
        // 거절(400 LEAVE-013)은 #276 으로 미뤘다 — 앱이 삭제 API 로 갈아타기 전에 켜면 그 구간 동안
        // 취소가 400 을 받아 사용자가 취소를 아예 못 한다. 지금 앱이 쓰는 경로라 열려 있어야 한다.
        mockMvc.perform(post(USAGES_URL).header(GUEST_HEADER, "leave-reversal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usedOn\": \"2026-05-09\", \"days\": -1, \"reason\": \"하루 취소\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.usages[0].days").value(-1.0));
    }

    @Test
    void 없는_내역을_지우면_404_LEAVE_012() throws Exception {
        mockMvc.perform(delete(USAGES_URL + "/{id}", 987654321L).header(GUEST_HEADER, "leave-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("LEAVE-012"))
                .andExpect(jsonPath("$.detail").value("연차 사용 내역을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 남의_내역은_지울_수_없고_없는_것과_같은_404다() throws Exception {
        // 403 으로 나눠 답하면 id 를 넣어보며 "이 번호는 있다" 를 알아낼 수 있다 — 코스 조회와 같은 규칙이다.
        String created = addUsage("leave-owner-x", "{\"usedOn\": \"2026-05-08\", \"days\": 1}")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long usageId = onlyUsageId(created);

        mockMvc.perform(delete(USAGES_URL + "/{id}", usageId).header(GUEST_HEADER, "leave-owner-y"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEAVE-012"));

        // 주인의 내역은 그대로 남아 있다.
        mockMvc.perform(get(URL).header(GUEST_HEADER, "leave-owner-x"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usages.length()").value(1));
    }

    @Test
    void 삭제도_빈_소유_키는_400_LEAVE_011() throws Exception {
        mockMvc.perform(delete(USAGES_URL + "/{id}", 1L).header(GUEST_HEADER, "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-011"));
    }

    /**
     * <b>#265 의 재현 시나리오 그 자체</b> — 지금 앱이 하는 그대로(상쇄 등록) 취소를 두 번 보낸 장부다.
     *
     * <p>넷을 한 번에 잠근다. ① 상쇄 등록은 아직 통과한다(#276 전이라 앱 경로가 열려 있어야 한다)
     * ② 원장 합이 -2 로 내려가도 <b>잔여가 17 이 아니라 15</b> — clamp 가 단위 테스트가 아니라 실응답에서 돈다
     * ③ 그래도 <b>음수 행은 목록에 그대로 보인다</b> — 마이그레이션으로 지우지 않기로 한 결정의 실제 모습이다
     * ④ 사용자가 그 행을 지울 수 있고, 지우면 clamp 가 가리고 있던 값과 장부가 같아진다.
     *
     * <p>#276 이 음수 등록을 닫으면 이 시나리오의 준비 단계는 API 로 만들 수 없다 — 그때 남아 있는 옛 행을
     * 재현하려면 도메인을 우회해 직접 적재해야 한다(하이드레이션은 생성자를 거치지 않는다).
     */
    @Test
    void 상쇄_등록이_쌓여도_잔여가_총을_넘지_않고_지워서_정리할_수_있다() throws Exception {
        String guest = "leave-reversal-ledger";
        mockMvc.perform(patch(URL).header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalDays\": 15}"))
                .andExpect(status().isOk());
        addUsage(guest, "{\"usedOn\": \"2026-05-08\", \"days\": 2}").andExpect(status().isCreated());
        // 같은 취소가 두 번 — 재시도·중복 탭이면 실제로 이렇게 들어온다. 원장 합이 -2 가 된다.
        addUsage(guest, "{\"usedOn\": \"2026-05-09\", \"days\": -2}").andExpect(status().isCreated());
        addUsage(guest, "{\"usedOn\": \"2026-05-09\", \"days\": -2}").andExpect(status().isCreated());

        String found = mockMvc.perform(get(URL).header(GUEST_HEADER, guest))
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
            mockMvc.perform(delete(USAGES_URL + "/{id}", id.longValue()).header(GUEST_HEADER, guest))
                    .andExpect(status().isOk());
        }

        // 정리하고 나면 clamp 가 가리고 있던 값과 실제 장부가 같아진다.
        mockMvc.perform(get(URL).header(GUEST_HEADER, guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedDays").value(2.0))
                .andExpect(jsonPath("$.data.remainingDays").value(13.0))
                .andExpect(jsonPath("$.data.usages.length()").value(1));
    }

    @Test
    void 반차는_0점5로_센다() throws Exception {
        String guest = "leave-half";
        mockMvc.perform(patch(URL).header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalDays\": 5}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(USAGES_URL).header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usedOn\": \"2026-05-07\", \"days\": 1.5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.remainingDays").value(3.5));
    }

    @Test
    void 남은_연차가_모자라도_막지_않고_음수로_내려준다() throws Exception {
        // 결정 #38 — 서버는 막지 않는다. 프론트가 경고하고 사용자가 확인하면 진행한다.
        String guest = "leave-over";
        mockMvc.perform(patch(URL).header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalDays\": 2}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(USAGES_URL).header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usedOn\": \"2026-05-08\", \"days\": 5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.remainingDays").value(-3.0));
    }

    @Test
    void 소유_키가_다르면_서로의_연차가_보이지_않는다() throws Exception {
        mockMvc.perform(patch(URL).header(GUEST_HEADER, "leave-owner-a")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalDays\": 20}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(URL).header(GUEST_HEADER, "leave-owner-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDays").value(0.0));
    }

    @Test
    void 총_연차가_0점5_단위가_아니면_400_LEAVE_009() throws Exception {
        mockMvc.perform(patch(URL).header(GUEST_HEADER, "leave-bad")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalDays\": 1.3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-009"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 총_연차가_음수면_400_LEAVE_009() throws Exception {
        mockMvc.perform(patch(URL).header(GUEST_HEADER, "leave-bad")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalDays\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-009"));
    }

    @Test
    void 총_연차가_상한을_넘으면_400_LEAVE_009() throws Exception {
        // 화면이 "최대 99일까지" 라고 안내한다 — 서버가 더 넉넉하면 화면을 안 거친 요청만 다른 규칙을 탄다(#142).
        mockMvc.perform(patch(URL).header(GUEST_HEADER, "leave-bad")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalDays\": 100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-009"));
    }

    @Test
    void 총_연차_경계값_0과_99는_받는다() throws Exception {
        // 화면 문구가 "0일보다 적게" · "최대 99일까지" 라 양끝은 유효하다.
        mockMvc.perform(patch(URL).header(GUEST_HEADER, "leave-edge-0")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalDays\": 0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDays").value(0.0));

        mockMvc.perform(patch(URL).header(GUEST_HEADER, "leave-edge-99")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalDays\": 99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDays").value(99.0));
    }

    @Test
    void 사용_증감이_0이면_400_LEAVE_010() throws Exception {
        mockMvc.perform(post(USAGES_URL).header(GUEST_HEADER, "leave-bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usedOn\": \"2026-05-08\", \"days\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-010"));
    }

    @Test
    void 소유_키_헤더가_없으면_400_COMMON_400() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    /**
     * 빈 헤더({@code X-Guest-Id: " "})는 {@code @RequestHeader} 를 통과한다 — 헤더가 '있긴 있기' 때문이다.
     * 그대로 흘려보내면 도메인 불변식에서 터져 <b>COMMON-500</b> 이 나갔다. 클라이언트 계약 위반이 서버 버그로
     * 보고되던 자리라, 세 엔드포인트가 <b>같은 계약</b>을 쓰는지 함께 확인한다.
     */
    @Test
    void 빈_소유_키는_세_엔드포인트_모두_400_LEAVE_011() throws Exception {
        mockMvc.perform(get(URL).header(GUEST_HEADER, "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-011"));

        mockMvc.perform(patch(URL).header(GUEST_HEADER, "  ")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalDays\": 10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-011"));

        mockMvc.perform(post(USAGES_URL).header(GUEST_HEADER, "  ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usedOn\": \"2026-05-08\", \"days\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-011"));
    }

    @Test
    void 소유_키가_너무_길면_400_LEAVE_011() throws Exception {
        mockMvc.perform(get(URL).header(GUEST_HEADER, "x".repeat(65)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-011"));
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
    void 같은_소유자로_동시에_수정해도_둘_다_성공한다() throws Exception {
        String guest = "leave-race";
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
                        statuses.add(mockMvc.perform(patch(URL).header(GUEST_HEADER, guest)
                                        .with(user("test"))
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
        mockMvc.perform(get(URL).header(GUEST_HEADER, guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDays").value(anyOf(is(10.0), is(11.0))));
    }
}
