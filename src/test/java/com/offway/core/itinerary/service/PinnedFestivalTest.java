package com.offway.core.itinerary.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.trip.domain.TravelWindow;
import com.offway.core.trip.service.dto.PoiCandidate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 축제를 열리는 날에 못 박는 규칙(#616).
 *
 * <p>예전에는 날 배정을 동선 순서에만 맡겼다. 축제가 그 줄의 어디에 놓이느냐는 좌표가 정하므로,
 * <b>2박3일 중 첫날만 하는 축제가 셋째 날 칸에 들어갔다</b> — 사용자가 문 닫힌 곳에 간다.
 *
 * <p>여기서 보는 것은 "어느 날에 놓이나" 와 "그 날에 어떻게 꽂히나" 다. 앞의 것이 틀리면 문 닫힌 곳에
 * 가고, 뒤의 것이 틀리면 하루 볼거리 수가 어긋난다.
 */
class PinnedFestivalTest {

    private static final LocalDate 오월일일 = LocalDate.of(2026, 5, 1);

    /** 첫날 볼거리 칸이 넉넉한 경우 — 대부분의 요청이 이렇다. */
    private static final int 칸넉넉 = 6;

    /** 늦게 도착해 첫날 볼거리 칸이 없는 경우 — 오전·오후가 다 지났다. */
    private static final int 첫날칸없음 = 0;

    /** 앞의 것부터 순서대로 집어 가는 규칙 — 실제 {@code takeVaried} 자리를 대신한다. */
    private static final BiFunction<List<PoiCandidate>, Integer, List<PoiCandidate>> 앞에서_집기 =
            (remaining, capacity) -> {
                List<PoiCandidate> picked = new ArrayList<>();
                while (!remaining.isEmpty() && picked.size() < capacity) {
                    picked.add(remaining.removeFirst());
                }
                return picked;
            };

    private static PoiCandidate 관광지(String 이름) {
        return PoiCandidate.builder()
                .contentId(이름).contentTypeId(12).title(이름).lat(36.3).lng(128.6).build();
    }

    /**
     * 표준데이터 축제 후보.
     *
     * <p><b>식별자는 {@code FST-<숫자>} 여야 한다.</b> 숫자가 아니면 {@code parsePublicId} 가 비어 있음을
     * 돌려주어 축제로 인식되지 않는다 — 이 테스트를 처음 쓸 때 이름을 그대로 붙였다가 여섯 케이스가
     * 한꺼번에 떨어졌다.
     */
    private static PoiCandidate 축제(String 이름, LocalDate 시작, LocalDate 종료) {
        return PoiCandidate.builder()
                .contentId("FST-" + Math.abs(이름.hashCode()))
                .contentTypeId(0).title(이름).lat(36.3).lng(128.6)
                .eventStart(시작).eventEnd(종료)
                .build();
    }

    /** 기간을 모르는 축제 — TourAPI 쪽에 있다. */
    private static PoiCandidate 기간모르는축제(String 이름) {
        return PoiCandidate.builder()
                .contentId("9999").contentTypeId(15).title(이름).lat(36.3).lng(128.6).build();
    }

    /**
     * <b>이것이 #616 의 본체다.</b>
     *
     * <p>첫날만 하는 축제는 1일차에, 셋째 날만 하는 축제는 3일차에 놓여야 한다. 예전에는 둘 다 좌표가
     * 정하는 아무 날에 놓였다.
     */
    @DisplayName("축제는 열리는 첫 날에 못 박힌다")
    @ParameterizedTest
    @CsvSource({
        "2026-05-01, 2026-05-01, 1, 첫날만 하는 축제",
        "2026-05-02, 2026-05-02, 2, 둘째날만 하는 축제",
        "2026-05-03, 2026-05-03, 3, 셋째날만 하는 축제",
        "2026-05-02, 2026-05-03, 2, 둘째~셋째날 (앞쪽을 쓴다)",
        "2026-04-20, 2026-05-10, 1, 여행을 통째로 덮음 (첫날)",
        "2026-04-20, 2026-05-02, 1, 여행 전에 시작해 둘째날 종료",
    })
    void 열리는_첫날에_놓인다(String 시작, String 종료, int 기대날, String 상황) {
        PoiCandidate festival = 축제("축제", LocalDate.parse(시작), LocalDate.parse(종료));

        PinnedFestival pinned =
                PinnedFestival.of(List.of(관광지("가"), festival, 관광지("나")), TravelWindow.of(오월일일, 3), 칸넉넉);

        assertEquals(기대날, pinned.dayNumber(), 상황);
        assertEquals(festival, pinned.festival(), 상황);
    }

    @DisplayName("축제가 없으면 못 박을 것이 없다")
    @Test
    void 축제가_없으면_못_박지_않는다() {
        PinnedFestival pinned =
                PinnedFestival.of(List.of(관광지("가"), 관광지("나")), TravelWindow.of(오월일일, 3), 칸넉넉);

        assertEquals(0, pinned.dayNumber());
    }

    /**
     * 기간을 모르는 축제는 못 박지 않는다 — "모른다" 를 "안 한다" 로도, "이 날이다" 로도 바꾸지 않는다.
     *
     * <p>일반 후보로 남으면 동선이 알맞은 자리에 놓는다.
     */
    @DisplayName("기간을 모르는 축제는 못 박지 않는다")
    @Test
    void 기간을_모르면_못_박지_않는다() {
        PinnedFestival pinned = PinnedFestival.of(
                List.of(관광지("가"), 기간모르는축제("어떤축제")), TravelWindow.of(오월일일, 3), 칸넉넉);

        assertEquals(0, pinned.dayNumber());
    }

    @DisplayName("여행일을 모르면 못 박지 않는다")
    @Test
    void 여행일을_모르면_못_박지_않는다() {
        PinnedFestival pinned =
                PinnedFestival.of(List.of(축제("축제", 오월일일, 오월일일)), null, 칸넉넉);

        assertEquals(0, pinned.dayNumber());
    }

    /**
     * 여행 중 하루도 안 열리는 축제 — 후보 필터와 어긋난 경우다.
     *
     * <p>못 박지 않고 일반 후보로 둔다. 어긋남이 코스를 비우게 만들지 않는다.
     */
    @DisplayName("하루도 안 열리면 못 박지 않는다")
    @Test
    void 여행중_안_열리면_못_박지_않는다() {
        PinnedFestival pinned = PinnedFestival.of(
                List.of(축제("지난축제", 오월일일.minusDays(30), 오월일일.minusDays(20))),
                TravelWindow.of(오월일일, 3), 칸넉넉);

        assertEquals(0, pinned.dayNumber());
    }

    @DisplayName("못 박은 축제는 일반 후보 줄에서 빠진다")
    @Test
    void 못_박은_축제는_일반_줄에서_빠진다() {
        PoiCandidate festival = 축제("축제", 오월일일, 오월일일);
        List<PoiCandidate> remaining = new ArrayList<>(List.of(관광지("가"), festival, 관광지("나")));

        PinnedFestival.of(remaining, TravelWindow.of(오월일일, 3), 칸넉넉).removeFrom(remaining);

        assertFalse(remaining.contains(festival), "안 빼면 엉뚱한 날이 먼저 집어 간다");
        assertEquals(2, remaining.size());
    }

    /**
     * <b>축제가 덧붙지 않는다.</b> 그날 볼거리 한 칸을 쓰는 것이므로 하루 볼거리 수가 늘지 않는다.
     */
    @DisplayName("축제를 놓는 날은 한 칸만 축제에 주고 나머지를 채운다")
    @Test
    void 축제날은_한_칸을_쓰고_나머지를_채운다() {
        PoiCandidate festival = 축제("축제", 오월일일, 오월일일);
        List<PoiCandidate> remaining =
                new ArrayList<>(List.of(관광지("가"), 관광지("나"), 관광지("다"), 관광지("라")));
        PinnedFestival pinned = new PinnedFestival(festival, 1);

        List<PoiCandidate> day1 = pinned.fill(1, remaining, 3, 앞에서_집기);

        assertEquals(3, day1.size(), "하루 볼거리 수가 늘었다");
        assertEquals(festival, day1.getFirst(), "축제가 그 날에 안 들어갔다");
        assertEquals(2, remaining.size(), "일반 후보를 두 개만 써야 한다");
    }

    @DisplayName("축제를 놓지 않는 날은 규칙대로만 채운다")
    @Test
    void 다른_날은_그대로_채운다() {
        PoiCandidate festival = 축제("축제", 오월일일, 오월일일);
        List<PoiCandidate> remaining = new ArrayList<>(List.of(관광지("가"), 관광지("나"), 관광지("다")));
        PinnedFestival pinned = new PinnedFestival(festival, 1);

        List<PoiCandidate> day2 = pinned.fill(2, remaining, 2, 앞에서_집기);

        assertEquals(2, day2.size());
        assertFalse(day2.contains(festival), "축제가 다른 날에 들어갔다");
    }

    /** 칸이 하나뿐인 날 — 늦게 시작하는 첫날이 이렇다. 축제만 놓는다. */
    @DisplayName("칸이 하나뿐인 날은 축제만 놓는다")
    @Test
    void 칸이_하나면_축제만_놓는다() {
        PoiCandidate festival = 축제("축제", 오월일일, 오월일일);
        List<PoiCandidate> remaining = new ArrayList<>(List.of(관광지("가"), 관광지("나")));

        List<PoiCandidate> day1 = new PinnedFestival(festival, 1).fill(1, remaining, 1, 앞에서_집기);

        assertEquals(List.of(festival), day1);
        assertEquals(2, remaining.size(), "일반 후보를 쓰지 않아야 한다");
    }

    /** 못 박은 것이 없으면 fill 은 규칙에 그대로 위임한다 — 기존 동작과 같다. */
    @DisplayName("못 박은 것이 없으면 기존 동작과 같다")
    @Test
    void 못_박은_것이_없으면_그대로다() {
        List<PoiCandidate> remaining = new ArrayList<>(List.of(관광지("가"), 관광지("나"), 관광지("다")));
        PinnedFestival none = PinnedFestival.of(List.of(관광지("가")), TravelWindow.of(오월일일, 3), 칸넉넉);

        List<PoiCandidate> day1 = none.fill(1, remaining, 2, 앞에서_집기);

        assertEquals(2, day1.size());
        assertTrue(day1.stream().noneMatch(PoiCandidate::isFestival));
    }

    /**
     * <b>칸이 없는 날에 축제를 넣지 않는다</b>(리뷰 지적).
     *
     * <p>늦게 도착하면 첫날 볼거리 칸이 0 이 될 수 있다 — {@code DayStart.sightCapacity} 가 오전·오후
     * 몫의 합이라 둘 다 지났으면 0 이다. 그 날에 슬롯을 만들면 <b>이미 지난 시간대에 일정</b>이 생긴다.
     *
     * <p>빈 날은 코스에서 통째로 빠지는 것이 정상이다({@code takeVaried} 가 capacity 0 에 빈 목록을
     * 돌려주는 것과 같은 결).
     */
    @DisplayName("칸이 0인 날은 축제를 넣지 않는다")
    @Test
    void 칸이_없으면_축제를_넣지_않는다() {
        PoiCandidate festival = 축제("축제", 오월일일, 오월일일);
        List<PoiCandidate> remaining = new ArrayList<>(List.of(관광지("가")));

        List<PoiCandidate> day1 = new PinnedFestival(festival, 1).fill(1, remaining, 0, 앞에서_집기);

        assertTrue(day1.isEmpty(), "쓸 수 있는 시간대가 없는 날에 슬롯을 만들었다");
        assertEquals(1, remaining.size(), "일반 후보도 쓰지 않아야 한다");
    }

    /**
     * 칸이 없는 첫날은 건너뛰고 <b>다음 개장일</b>에 놓는다.
     *
     * <p>축제를 버리지 않으려는 것이다. 첫날만 칸이 줄고 나머지 날은 {@code fullDay()} 라 칸이 있다.
     */
    @DisplayName("첫날 칸이 없으면 다음 개장일에 놓는다")
    @Test
    void 첫날_칸이_없으면_다음_개장일에_놓는다() {
        PoiCandidate festival = 축제("사흘하는축제", 오월일일, 오월일일.plusDays(2));

        PinnedFestival pinned = PinnedFestival.of(
                List.of(관광지("가"), festival), TravelWindow.of(오월일일, 3), 첫날칸없음);

        assertEquals(2, pinned.dayNumber(), "첫날을 건너뛰고 2일차에 놓아야 한다");
    }

    /**
     * 첫날에만 열리는데 그 날 칸이 없으면 못 박지 않는다.
     *
     * <p>축제를 잃지만, <b>갈 수 없는 시간에 일정을 주는 것보다 낫다</b>. 일반 후보로 남아 동선이
     * 판단한다.
     */
    @DisplayName("첫날만 열리는데 칸이 없으면 못 박지 않는다")
    @Test
    void 첫날만_열리는데_칸이_없으면_못_박지_않는다() {
        PoiCandidate festival = 축제("첫날만하는축제", 오월일일, 오월일일);

        PinnedFestival pinned = PinnedFestival.of(
                List.of(관광지("가"), festival), TravelWindow.of(오월일일, 3), 첫날칸없음);

        assertEquals(0, pinned.dayNumber());
    }

    /** 첫날 칸이 있으면 종전대로 첫날에 놓는다 — 건너뛰기가 과하게 돌지 않는지 본다. */
    @DisplayName("첫날 칸이 있으면 첫날에 놓는다")
    @Test
    void 첫날_칸이_있으면_첫날에_놓는다() {
        PoiCandidate festival = 축제("사흘하는축제", 오월일일, 오월일일.plusDays(2));

        PinnedFestival pinned = PinnedFestival.of(
                List.of(관광지("가"), festival), TravelWindow.of(오월일일, 3), 칸넉넉);

        assertEquals(1, pinned.dayNumber());
    }
}
