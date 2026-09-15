package com.offway.core.liveactivity.controller;

import static com.offway.core.user.config.TestLogins.loginAs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.Density;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.itinerary.domain.SlotDisplay;
import com.offway.core.itinerary.domain.SlotKind;
import com.offway.core.itinerary.domain.TimeOfDay;
import com.offway.core.itinerary.repository.CourseRepository;
import com.offway.core.leave.domain.StartDayLeave;
import com.offway.core.liveactivity.domain.LiveActivityToken;
import com.offway.core.liveactivity.repository.LiveActivityTokenRepository;
import com.offway.core.transport.domain.TransportMode;
import com.offway.core.user.config.WithLoginUser;
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
 * 잠금화면 갱신 토큰 등록·해제의 HTTP 계약(#575).
 *
 * <p>여기서 지키는 것 둘.
 *
 * <ul>
 *   <li><b>멱등</b> — 앱은 네트워크가 끊기면 성공 여부를 모른 채 재시도하고, iOS 가 토큰을 갱신해 줄
 *       때도 같은 요청을 다시 보낸다. 행이 늘면 같은 잠금화면에 갱신이 두 번 간다
 *   <li><b>남의 코스로 등록되지 않는다</b> — 이 등록의 대가로 서버가 그 코스의 여행지·날짜를 매일
 *       잠금화면에 그려 준다. 확인을 빠뜨리면 코스 id 를 적는 것만으로 남의 여행 일정을 받아 본다
 * </ul>
 *
 * <p><b>클래스 레벨 {@code @Transactional} 로 롤백한다</b>(테스트 규약, #577 리뷰). 여기서 남긴 등록은
 * 소유자를 가리지 않고 전부 훑는 자정 배치 테스트({@code LiveActivityRefreshIntegrationTest})의 대상에
 * 섞인다 — 임의 UUID 는 소유자 충돌만 줄일 뿐 남은 행을 치우지 않는다. 저장 경로가 전부 테스트 스레드
 * 에서 도므로(MockMvc 요청·{@code CourseRepository.save}) 함께 롤백된다.
 *
 * <p>그래도 소유자·토큰은 시나리오마다 다르게 쓴다 — 트랜잭션 안에서도 한 메서드가 자기 데이터로
 * 완결되는 편이 읽기 쉽다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithLoginUser
@Transactional
class LiveActivityIntegrationTest {

    private static final String URL = "/api/v1/live-activities";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LiveActivityTokenRepository liveActivityTokenRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Test
    void 등록하면_200과_빈_data를_준다() throws Exception {
        UUID owner = UUID.randomUUID();
        Course course = saveCourse(owner);
        String token = uniqueToken();

        mockMvc.perform(post(URL).with(loginAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(course.getId(), token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                // 201 이 아니다 — 새로 만드는지 고쳐 쓰는지가 요청마다 달라 만들었다고 단정할 수 없다.
                .andExpect(jsonPath("$.data").doesNotExist());

        assertEquals(1, rowsOf(owner).size());
        assertEquals(token, rowsOf(owner).getFirst().getToken());
    }

    @Test
    void 같은_코스로_다시_보내면_행이_늘지_않고_토큰만_갈린다() throws Exception {
        UUID owner = UUID.randomUUID();
        Course course = saveCourse(owner);
        String first = uniqueToken();
        String second = uniqueToken();

        register(owner, course.getId(), first);
        register(owner, course.getId(), second);

        List<LiveActivityToken> rows = rowsOf(owner);
        assertEquals(1, rows.size(), "행이 늘었다 — 같은 잠금화면에 갱신이 두 번 간다");
        assertEquals(second, rows.getFirst().getToken());
    }

    @Test
    void 코스가_다르면_행이_따로_생긴다() throws Exception {
        // 같은 기기에서 코스 둘을 띄우면 카드도 둘이다 — 토큰도 따로다.
        UUID owner = UUID.randomUUID();
        Course one = saveCourse(owner);
        Course other = saveCourse(owner);

        register(owner, one.getId(), uniqueToken());
        register(owner, other.getId(), uniqueToken());

        assertEquals(2, rowsOf(owner).size());
    }

    /**
     * 남의 코스로는 등록되지 않는다 — <b>404</b>.
     *
     * <p>"있는데 남의 것" 과 "아예 없다" 를 가르지 않는다. 가르면 코스 id 를 훑는 것만으로 남의 코스가
     * 존재하는지 알아낼 수 있다.
     */
    @Test
    void 남의_코스로는_등록되지_않는다() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        Course course = saveCourse(owner);

        mockMvc.perform(post(URL).with(loginAs(stranger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(course.getId(), uniqueToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("LIVE-003"));

        assertTrue(rowsOf(stranger).isEmpty(), "남의 코스로 등록이 만들어졌다");
    }

    @Test
    void 없는_코스는_404다() throws Exception {
        mockMvc.perform(post(URL).with(loginAs(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(999_999_999L, uniqueToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LIVE-003"));
    }

    @Test
    void 토큰이_비면_400이다() throws Exception {
        UUID owner = UUID.randomUUID();
        Course course = saveCourse(owner);

        mockMvc.perform(post(URL).with(loginAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(course.getId(), "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void 코스_id가_0_이하면_400이다() throws Exception {
        mockMvc.perform(post(URL).with(loginAs(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(0L, uniqueToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void 해제하면_그_코스의_등록만_사라진다() throws Exception {
        UUID owner = UUID.randomUUID();
        Course kept = saveCourse(owner);
        Course removed = saveCourse(owner);
        register(owner, kept.getId(), uniqueToken());
        register(owner, removed.getId(), uniqueToken());

        mockMvc.perform(delete(URL + "/" + removed.getId()).with(loginAs(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());

        List<LiveActivityToken> rows = rowsOf(owner);
        assertEquals(1, rows.size());
        assertEquals(kept.getId(), rows.getFirst().getCourseId());
    }

    /** 지울 것이 없어도 성공이다 — 원한 상태가 이미 이뤄져 있는데 404 를 띄울 이유가 없다. */
    @Test
    void 지울_등록이_없어도_200이다() throws Exception {
        mockMvc.perform(delete(URL + "/424242").with(loginAs(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    /** 남의 코스 id 를 적어도 아무 일이 없다 — 지우는 범위가 이미 이 사람의 행으로 좁혀져 있다. */
    @Test
    void 남의_코스를_해제해도_남의_등록은_그대로다() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        Course course = saveCourse(owner);
        register(owner, course.getId(), uniqueToken());

        mockMvc.perform(delete(URL + "/" + course.getId()).with(loginAs(stranger)))
                .andExpect(status().isOk());

        assertEquals(1, rowsOf(owner).size(), "남이 부른 해제로 내 등록이 사라졌다");
    }

    private void register(UUID owner, Long courseId, String token) throws Exception {
        mockMvc.perform(post(URL).with(loginAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(courseId, token)))
                .andExpect(status().isOk());
    }

    private List<LiveActivityToken> rowsOf(UUID owner) {
        return liveActivityTokenRepository.findAll().stream()
                .filter(row -> owner.equals(row.getUserId()))
                .toList();
    }

    private Course saveCourse(UUID owner) {
        return courseRepository.save(Course.ownedBy(
                owner,
                1L,
                Density.RELAXED,
                TransportMode.CAR,
                List.of(DaySchedule.of(1, List.of(minimalSlot()))),
                LocalDate.of(2097, 1, 1),
                1,
                null,
                StartDayLeave.DEFAULT,
                null));
    }

    private static Slot minimalSlot() {
        return Slot.of(1, TimeOfDay.MORNING, SlotKind.SIGHT, "c1", "장소1", 37.50, 128.60, 0,
                new SlotDisplay(null, null, null, null));
    }

    private static String body(Long courseId, String token) {
        return "{\"courseId\": %s, \"pushToken\": \"%s\"}".formatted(courseId, token);
    }

    private static String uniqueToken() {
        return "la-" + UUID.randomUUID();
    }
}
