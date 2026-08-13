package com.offway.core.itinerary.controller;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.CourseShare;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.Density;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.itinerary.domain.SlotDisplay;
import com.offway.core.itinerary.domain.SlotKind;
import com.offway.core.itinerary.domain.TimeOfDay;
import com.offway.core.itinerary.repository.CourseRepository;
import com.offway.core.itinerary.repository.CourseShareRepository;
import com.offway.core.transport.domain.TransportMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 코스 공유 링크(#143) — 공개 조회의 HTTP 계약.
 *
 * <p><b>클래스에 {@code @WithMockUser} 를 걸지 않는다.</b> 이 경로가 인증 게이트를 통과하는지가 검증
 * 대상이라, 인증을 걸어두면 정작 확인하려던 것이 가려진다. 코스를 만드는 준비 요청에만 {@code user()} 를 붙인다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CourseShareIntegrationTest {

    private static final String COURSES_URL = "/api/v1/courses";
    private static final String SHARE_URL = "/api/v1/courses/share";
    private static final String PUBLIC_URL = "/api/v1/public/courses/{shareToken}";
    private static final String GUEST_HEADER = "X-Guest-Id";

    /** 128비트를 URL-safe base64(패딩 없음)로 적으면 22자다 — 규격이 바뀌면 뿌린 링크가 깨진다. */
    private static final int TOKEN_LENGTH = 22;

    // 정선(16) 당일치기 · 유효한 코스
    private static final String VALID_BODY =
            """
            { "regionId": 16, "density": "PACKED", "transport": "CAR", "days": [
              { "day": 1, "items": [
                {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c1","title":"장소1","lat":37.50,"lng":128.60,"travelMinutes":0},
                {"order":2,"timeOfDay":"LUNCH","kind":"FOOD","poiContentId":"c2","title":"맛집1","lat":37.51,"lng":128.61,"travelMinutes":15}
              ]}
            ]}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseShareRepository courseShareRepository;

    @Test
    void 저장하면_공유토큰이_함께_온다() throws Exception {
        mockMvc.perform(post(COURSES_URL)
                        .with(user("dev"))
                        .header(GUEST_HEADER, guest())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.shareToken", notNullValue()));
    }

    @Test
    void 공유토큰이면_인증_없이_코스를_본다() throws Exception {
        String token = saveAndGetToken(guest());

        mockMvc.perform(get(PUBLIC_URL, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.regionId").value(16))
                .andExpect(jsonPath("$.data.days[0].items[0].title").value("장소1"));
    }

    /** 링크를 받은 사람은 수정·삭제를 못 하므로 내부 순번을 알 이유가 없다. 알려주면 다른 경로를 두드릴 단서만 준다. */
    @Test
    void 공개_응답에는_내부_식별자와_토큰이_없다() throws Exception {
        String token = saveAndGetToken(guest());

        mockMvc.perform(get(PUBLIC_URL, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseId").doesNotExist())
                .andExpect(jsonPath("$.data.shareToken").doesNotExist());
    }

    @Test
    void 없는_토큰이면_404_다() throws Exception {
        mockMvc.perform(get(PUBLIC_URL, "AAAAAAAAAAAAAAAAAAAAAA"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-008"));
    }

    /**
     * 게시자가 지운 코스는 "없는 링크"(404)가 아니라 410 이다.
     *
     * <p>둘을 뭉치면 받은 사람이 자기가 링크를 잘못 눌렀다고 오해한다. 삭제됐다는 사실을 알려줘야 한다.
     */
    @Test
    void 게시자가_코스를_지우면_410_으로_삭제를_알린다() throws Exception {
        String guest = guest();
        String token = saveAndGetToken(guest);

        mockMvc.perform(delete(COURSES_URL + "/" + courseIdOf(guest))
                        .with(user("dev"))
                        .header(GUEST_HEADER, guest))
                .andExpect(status().isOk());

        mockMvc.perform(get(PUBLIC_URL, token))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.code").value("ITINERARY-009"));
    }

    /**
     * 공유 웹앱은 브라우저에서 부르므로 CORS 가 열려 있어야 한다. 설정만 넣고 확인하지 않으면 프론트가
     * 붙이는 순간에야 막힌 것을 알게 된다.
     */
    @Test
    void 공개_경로는_브라우저_오리진에_열려_있다() throws Exception {
        String token = saveAndGetToken(guest());

        mockMvc.perform(get(PUBLIC_URL, token).header("Origin", "https://offway.vercel.app"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://offway.vercel.app"))
                // 자격증명은 막는다 — 이 접두어 아래로 브라우저 쿠키·인증 헤더가 딸려가지 않게.
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    /**
     * 상세에도 <b>저장 때와 같은</b> 토큰이 실린다(#259).
     *
     * <p>다른 토큰이 나오면 먼저 뿌린 링크가 죽는다. "실린다" 만 보면 그 회귀를 못 잡으므로 값을 대조한다.
     */
    @Test
    void 상세_응답에_저장_때와_같은_공유토큰이_실린다() throws Exception {
        String guest = guest();
        String saved = saveAndGetToken(guest);

        mockMvc.perform(get(COURSES_URL + "/" + courseIdOf(guest))
                        .with(user("dev"))
                        .header(GUEST_HEADER, guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.shareToken").value(saved));
    }

    @Test
    void 목록_항목에도_같은_공유토큰이_실린다() throws Exception {
        String guest = guest();
        String saved = saveAndGetToken(guest);

        mockMvc.perform(get(COURSES_URL).with(user("dev")).header(GUEST_HEADER, guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].shareToken").value(saved));
    }

    /** 목록은 소유자 범위로만 조회한다 — 남의 코스가 섞이면 그 사람의 공유 링크까지 함께 새어 나간다. */
    @Test
    void 목록은_남의_코스와_토큰을_주지_않는다() throws Exception {
        String owner = guest();
        saveAndGetToken(owner);
        String stranger = guest();

        mockMvc.perform(get(COURSES_URL).with(user("dev")).header(GUEST_HEADER, stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        // 남의 코스 ID 를 직접 알아도 상세로 열 수 없다 — 토큰이 새는 다른 문이 없는지 함께 본다.
        mockMvc.perform(get(COURSES_URL + "/" + courseIdOf(owner))
                        .with(user("dev"))
                        .header(GUEST_HEADER, stranger))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-003"));
    }

    /**
     * 공유 행이 없는 코스(#143 이전 저장분)를 상세로 열면 <b>그 자리에서 발급</b>된다(#259).
     *
     * <p>발급하지 않으면 그 코스들은 영영 공유 불가로 남는다. 저장 API 를 거치면 항상 토큰이 붙으므로,
     * 토큰 없는 상태는 리포지토리로 직접 만들어야 재현된다.
     */
    @Test
    void 토큰이_없던_코스는_상세를_열면_발급된다() throws Exception {
        String guest = guest();
        long courseId = saveWithoutShare(guest);

        String first = tokenFromDetail(guest, courseId);
        assertEquals(TOKEN_LENGTH, first.length());

        // 두 번째 조회가 새 토큰을 발급하면 먼저 뿌린 링크가 죽는다.
        assertEquals(first, tokenFromDetail(guest, courseId));

        mockMvc.perform(get(PUBLIC_URL, first)).andExpect(status().isOk());
    }

    /**
     * 목록은 없는 토큰을 <b>발급하지 않는다</b>(#259).
     *
     * <p>페이지당 최대 100건이라, 목록에서 발급하면 조회 한 번이 그만큼의 INSERT 가 된다. null 만 단언하면
     * "발급은 했는데 응답에 안 실었다" 와 구분되지 않으므로 <b>공유 행이 안 생겼는지</b>까지 본다.
     */
    @Test
    void 목록은_없는_공유토큰을_발급하지_않는다() throws Exception {
        String guest = guest();
        long courseId = saveWithoutShare(guest);

        mockMvc.perform(get(COURSES_URL).with(user("dev")).header(GUEST_HEADER, guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].courseId").value((int) courseId))
                .andExpect(jsonPath("$.data[0].shareToken").doesNotExist());

        assertTrue(courseShareRepository.findByCourseId(courseId).isEmpty(),
                "목록 조회가 공유 행을 만들었다 — 한 페이지가 최대 100 INSERT 가 된다");
    }

    /** 날짜를 고친 순간 토큰이 빠지면 화면의 공유 버튼이 사라진다 — 응답 모양이 상세와 같아야 한다. */
    @Test
    void 여행_날짜를_고쳐도_공유토큰이_그대로_실린다() throws Exception {
        String guest = guest();
        String saved = saveAndGetToken(guest);

        mockMvc.perform(patch(COURSES_URL + "/" + courseIdOf(guest))
                        .with(user("dev"))
                        .header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"travelDate\":\"" + LocalDate.now().plusDays(30) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shareToken").value(saved));
    }

    /** 담기 전 추천 결과 화면의 공유 버튼(#261) — 담지 않고 링크만 받는다. */
    @Test
    void 담지_않고_공유하면_토큰만_받고_링크가_열린다() throws Exception {
        String token = shareWithoutSaving(VALID_BODY);

        mockMvc.perform(get(PUBLIC_URL, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.regionId").value(16))
                .andExpect(jsonPath("$.data.days[0].items[0].title").value("장소1"))
                .andExpect(jsonPath("$.data.courseId").doesNotExist());
    }

    /**
     * 담지 않은 코스는 <b>반드시 공유 행과 짝</b>이다(#261) — 나중에 붙일 정리가 이 전제 위에 선다.
     *
     * <p>코스만 저장되고 링크 발급이 실패하면 그 코스는 아무도 닿을 수 없다. 주인이 없어 목록·상세·삭제
     * 어디에도 안 걸리고, 공유 행이 없어 링크로도 못 연다. 정리는 <b>공유 행의 발급 시각으로 나이를 재므로</b>
     * 그 정리조차 못 찾는다 — 짝이 깨지면 영영 남는 죽은 데이터가 된다.
     */
    @Test
    void 담지_않은_코스는_공유_행과_함께_저장된다() throws Exception {
        String token = shareWithoutSaving(VALID_BODY);

        CourseShare share = courseShareRepository
                .findByShareToken(token)
                .orElseThrow(() -> new AssertionError("발급한 토큰으로 공유 행을 못 찾는다"));
        assertTrue(courseRepository.findById(share.getCourseId()).isPresent(),
                "공유 행이 가리키는 코스가 없다 — 링크가 열리지 않는다");
    }

    /**
     * 담지 않은 코스가 목록에 끼면 사용자는 담지 않은 것을 담았다고 오해한다.
     *
     * <p>주인 없이 보관하는 것이 그 장치다 — "내 코스" 조회가 전부 게스트 범위라 어느 질의에도 안 걸린다.
     */
    @Test
    void 담지_않고_공유한_코스는_내_코스에_나오지_않는다() throws Exception {
        String guest = guest();
        shareWithoutSaving(VALID_BODY);

        mockMvc.perform(get(COURSES_URL).with(user("dev")).header(GUEST_HEADER, guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    /** 링크로 열리는 코스가 담은 코스보다 느슨하면, 같은 payload 가 한쪽에서만 통과한다. */
    @Test
    void 담지_않는_공유도_구성이_틀리면_400_이다() throws Exception {
        String invalid = VALID_BODY.replace("\"order\":2", "\"order\":3");

        mockMvc.perform(post(SHARE_URL)
                        .with(user("dev"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("ITINERARY-002"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    /**
     * 같은 코스를 두 번 보내면 링크도 두 개다 — 요청 본문만으로는 같은 코스인지 알 근거가 없다.
     *
     * <p>담은 코스의 링크가 코스당 하나인 것과 다른 점이라 문서에 적었다. 여기서 잠가 둔다.
     */
    @Test
    void 담지_않는_공유는_부를_때마다_새_링크다() throws Exception {
        String first = shareWithoutSaving(VALID_BODY);
        String second = shareWithoutSaving(VALID_BODY);

        assertNotEquals(first, second);
        mockMvc.perform(get(PUBLIC_URL, first)).andExpect(status().isOk());
        mockMvc.perform(get(PUBLIC_URL, second)).andExpect(status().isOk());
    }

    private String shareWithoutSaving(String body) throws Exception {
        String response = mockMvc.perform(post(SHARE_URL)
                        .with(user("dev"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.code").value("OK"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.data.shareToken");
    }

    private String tokenFromDetail(String guest, long courseId) throws Exception {
        String body = mockMvc.perform(get(COURSES_URL + "/" + courseId)
                        .with(user("dev"))
                        .header(GUEST_HEADER, guest))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.data.shareToken");
    }

    /** 저장 API 를 거치지 않고 코스만 만든다 — 공유 행이 없는 상태를 재현하는 유일한 길이다. */
    private long saveWithoutShare(String guest) {
        Slot slot = Slot.of(1, TimeOfDay.MORNING, SlotKind.SIGHT, "c1", "장소1", 37.50, 128.60, 0,
                new SlotDisplay(null, null, null, null));
        Course course = Course.ownedBy(
                guest, 16L, Density.PACKED, TransportMode.CAR,
                List.of(DaySchedule.of(1, List.of(slot))), null, 1, null);
        return courseRepository.save(course).getId();
    }

    private String saveAndGetToken(String guest) throws Exception {
        String body = mockMvc.perform(post(COURSES_URL)
                        .with(user("dev"))
                        .header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.data.shareToken");
    }

    private long courseIdOf(String guest) throws Exception {
        String body = mockMvc.perform(get(COURSES_URL)
                        .with(user("dev"))
                        .header(GUEST_HEADER, guest))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.data[0].courseId")).longValue();
    }

    /** DB 격리: 롤백 대신 테스트마다 고유 게스트 ID 를 써서 "내 코스" 목록이 섞이지 않게 한다. */
    private static String guest() {
        return "share-" + UUID.randomUUID();
    }
}
