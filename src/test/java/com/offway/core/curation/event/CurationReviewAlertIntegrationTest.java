package com.offway.core.curation.event;

import static com.offway.core.user.config.TestLogins.loginAsAdmin;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.offway.core.common.notification.Notifier;
import com.offway.core.curation.repository.CuratedLinkJpaRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 큐레이션 링크 추가 알림(#613) — <b>생성만, 커밋 뒤에, 백오피스로 데려간다</b>.
 *
 * <h2>왜 통합으로 보나</h2>
 *
 * <p>값어치는 "이벤트가 발행된다" 가 아니라 <b>"백오피스에서 만들면 알림이 나간다"</b> 다. 그 사이에
 * 트랜잭션 경계와 {@code AFTER_COMMIT} 이 있어서, 단위 테스트로는 정작 틀릴 수 있는 자리를 못 본다.
 *
 * <p><b>{@code @Transactional} 을 붙이지 않는다.</b> 붙이면 커밋이 없어 {@code AFTER_COMMIT} 리스너가
 * 아예 안 돌고, 그러면 이 테스트가 통째로 헛돈다(가입 알림 테스트와 같은 판단). 대신 만든 행을 본문에서
 * 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CurationReviewAlertIntegrationTest {

    private static final String URL = "/api/v1/admin/curated-links";

    /** 보낸 문구를 모으는 대체 구현 — 디스코드는 외부 경계라 실제로 부르지 않는다. */
    static class CapturingNotifier implements Notifier {

        private final List<String> sent = new CopyOnWriteArrayList<>();

        @Override
        public void send(String message) {
            sent.add(message);
        }

        List<String> drain() {
            List<String> copy = new ArrayList<>(sent);
            sent.clear();
            return copy;
        }
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        Notifier capturingNotifier() {
            return new CapturingNotifier();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Notifier notifier;

    @Autowired
    private CuratedLinkJpaRepository curatedLinkJpaRepository;

    private CapturingNotifier captured() {
        return (CapturingNotifier) notifier;
    }

    /** 검토에 필요한 것이 문구에 다 들어 있나 — 제목·칩 문구·노출 면·게시 상태·백오피스 주소. */
    @Test
    void 링크를_추가하면_검토_요청이_나간다() throws Exception {
        captured().drain();
        long id = create("""
                { "title": "여객선 예매", "chipText": "섬 배편 예매",
                  "linkUrl": "https://island.haewoon.co.kr", "alwaysOn": true,
                  "surfaces": ["REGION"], "displayOrder": 7, "published": false }""");
        try {
            List<String> sent = captured().drain();

            assertEquals(1, sent.size(), "알림이 한 건 나가야 한다");
            String message = sent.get(0);
            assertTrue(message.contains("여객선 예매"), "제목이 없으면 목록에서 못 찾는다: " + message);
            assertTrue(message.contains("섬 배편 예매"), "칩 문구가 없으면 어색한지 판단할 수 없다");
            assertTrue(message.contains("REGION"), "어느 면에 뜨는지가 없다");
            assertTrue(message.contains("OFF"), "꺼진 채 만들어졌다는 사실이 빠졌다");
            assertTrue(message.contains("/admin/"), "백오피스로 데려가지 않는다");
        } finally {
            curatedLinkJpaRepository.deleteById(id);
        }
    }

    /**
     * 링크 주소는 싣지 않는다.
     *
     * <p>검토는 백오피스에서 하는 것이고, 외부 주소를 실으면 디스코드가 미리보기를 펼친다. 무엇보다
     * <b>알림만 보고 판단하게 만든다</b> — 켤지는 칩 문구·노출 면·기간을 함께 봐야 정해진다.
     */
    @Test
    void 링크_주소는_알림에_싣지_않는다() throws Exception {
        captured().drain();
        long id = create("""
                { "title": "가보고싶은섬", "chipText": "섬 여행 정보",
                  "linkUrl": "https://island.haewoon.co.kr/secret-path", "alwaysOn": true,
                  "surfaces": ["HOME"], "displayOrder": 8, "published": false }""");
        try {
            String message = captured().drain().get(0);

            assertFalse(message.contains("island.haewoon.co.kr"), "링크 주소가 문구에 실렸다: " + message);
            assertFalse(message.contains("secret-path"), "경로까지 실렸다");
        } finally {
            curatedLinkJpaRepository.deleteById(id);
        }
    }

    /** 켜진 채로 만들어졌으면 그렇게 말한다 — 이미 사용자에게 나간 것이라 검토가 더 급하다. */
    @Test
    void 켜진_채_만들면_게시_ON_으로_알린다() throws Exception {
        captured().drain();
        long id = create("""
                { "title": "관광두레", "chipText": "지역 사업체",
                  "linkUrl": "https://tourdure.visitkorea.or.kr", "alwaysOn": true,
                  "surfaces": ["REGION"], "displayOrder": 9, "published": true }""");
        try {
            assertTrue(captured().drain().get(0).contains("ON"), "켜진 것을 OFF 로 알렸다");
        } finally {
            curatedLinkJpaRepository.deleteById(id);
        }
    }

    /**
     * 조회·수정으로는 안 나간다 — <b>이게 없으면 알림 폭탄 회귀를 못 잡는다.</b>
     *
     * <p>게시 상태를 바꾸는 것은 이미 검토의 <b>결과</b>다. 그것까지 알리면 정작 봐야 할 줄이 묻힌다.
     */
    @Test
    void 조회와_수정으로는_알리지_않는다() throws Exception {
        long id = create("""
                { "title": "문화포털", "chipText": "축제 보기",
                  "linkUrl": "https://www.culture.go.kr", "alwaysOn": true,
                  "surfaces": ["HOME"], "displayOrder": 10, "published": false }""");
        try {
            captured().drain();

            mockMvc.perform(get(URL).with(loginAsAdmin(UUID.randomUUID())))
                    .andExpect(status().isOk());
            mockMvc.perform(patch(URL + "/{id}", id)
                            .with(loginAsAdmin(UUID.randomUUID()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "title": "문화포털", "chipText": "축제 보기",
                                      "linkUrl": "https://www.culture.go.kr", "alwaysOn": true,
                                      "surfaces": ["HOME"], "published": true }"""))
                    .andExpect(status().isOk());

            assertEquals(List.of(), captured().drain(), "생성 아닌 것으로 알림이 나갔다");
        } finally {
            curatedLinkJpaRepository.deleteById(id);
        }
    }

    private long create(String body) throws Exception {
        String response = mockMvc.perform(post(URL)
                        .with(loginAsAdmin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.id")).longValue();
    }
}
