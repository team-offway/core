package com.offway.core.itinerary.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.itinerary.domain.Density;
import com.offway.core.itinerary.domain.ItineraryException;
import com.offway.core.itinerary.service.dto.GenerateCourse;
import com.offway.core.itinerary.service.dto.GeneratedCourse;
import com.offway.core.leave.domain.StartDayLeave;
import com.offway.core.transport.domain.TransportMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 방금 만든 코스를 다시 주는 규칙(#584).
 *
 * <p>여기서 잠그는 것은 <b>캐시가 거짓말을 안 하는가</b> 하나다. 같은 요청에만 같은 값을 주고, 다른
 * 요청에는 새로 만들고, <b>실패는 굳히지 않는다</b>.
 */
class RecentCoursesTest {

    private static final LocalDate TRAVEL_DATE = LocalDate.of(2026, 9, 23);

    @Test
    void 같은_요청이면_다시_만들지_않는다() {
        RecentCourses recent = new RecentCourses();
        AtomicInteger built = new AtomicInteger();
        GenerateCourse command = command(76L, 2, TransportMode.CAR);

        GeneratedCourse first = recent.get(command, () -> built.incrementAndGet() > 0 ? course() : null);
        GeneratedCourse second = recent.get(command, () -> built.incrementAndGet() > 0 ? course() : null);

        assertEquals(1, built.get(), "같은 요청인데 또 만들었다 — 외부 호출이 그만큼 또 나간다");
        assertSame(first, second, "같은 요청에 다른 코스를 줬다");
    }

    /**
     * 커맨드가 한 칸이라도 다르면 <b>다른 코스</b>다.
     *
     * <p>{@code GenerateCourse} 가 record 라 값 비교가 자동이지만, 그 성질에 기대고 있다는 사실을
     * 테스트로 박아 둔다 — 누군가 클래스로 바꾸면 여기가 먼저 깨진다.
     */
    @Test
    void 지역이_다르면_새로_만든다() {
        RecentCourses recent = new RecentCourses();
        AtomicInteger built = new AtomicInteger();

        recent.get(command(76L, 2, TransportMode.CAR), () -> counted(built));
        recent.get(command(1L, 2, TransportMode.CAR), () -> counted(built));

        assertEquals(2, built.get());
    }

    @Test
    void 이동수단이_다르면_새로_만든다() {
        RecentCourses recent = new RecentCourses();
        AtomicInteger built = new AtomicInteger();

        recent.get(command(76L, 2, TransportMode.CAR), () -> counted(built));
        recent.get(command(76L, 2, TransportMode.TRANSIT), () -> counted(built));

        assertEquals(2, built.get());
    }

    @Test
    void 기간이_다르면_새로_만든다() {
        RecentCourses recent = new RecentCourses();
        AtomicInteger built = new AtomicInteger();

        recent.get(command(76L, 2, TransportMode.CAR), () -> counted(built));
        recent.get(command(76L, 3, TransportMode.CAR), () -> counted(built));

        assertEquals(2, built.get());
    }

    /**
     * <b>실패를 굳히지 않는다.</b>
     *
     * <p>코스를 못 만드는 것은 사용자에게 닿아야 하는 결과이고, 그 실패를 1분간 들고 있으면 그사이
     * 후보가 채워져도 계속 같은 오류가 나간다.
     */
    @Test
    void 실패는_캐시하지_않는다() {
        RecentCourses recent = new RecentCourses();
        AtomicInteger tried = new AtomicInteger();
        GenerateCourse command = command(76L, 2, TransportMode.CAR);

        assertThrows(ItineraryException.class, () -> recent.get(command, () -> {
            tried.incrementAndGet();
            throw ItineraryException.courseNotBuildable();
        }));
        assertThrows(ItineraryException.class, () -> recent.get(command, () -> {
            tried.incrementAndGet();
            throw ItineraryException.courseNotBuildable();
        }));

        assertEquals(2, tried.get(), "실패를 굳혔다 — 후보가 채워져도 같은 오류가 계속 나간다");
    }

    /**
     * <b>실패를 삼키지 않는다.</b>
     *
     * <p>{@code ExternalDataCache} 를 안 쓴 이유가 이것이다 — 그쪽은 loader 예외를 잡아 폴백으로
     * degrade 하는 것이 계약이라, 여기 쓰면 빈 코스가 200 으로 나간다.
     */
    @Test
    void 실패는_그대로_올라간다() {
        RecentCourses recent = new RecentCourses();

        assertThrows(ItineraryException.class,
                () -> recent.get(command(76L, 2, TransportMode.CAR), () -> {
                    throw ItineraryException.courseNotBuildable();
                }));
    }

    /**
     * 개수 상한이 실제로 걸린다.
     *
     * <p>커맨드에 출발지 좌표가 들어 있어 <b>키 공간이 무한하다</b>. TTL 은 값의 신선도만 관리하고
     * 엔트리를 지우지 않으므로(성능 규약), 상한이 없으면 800MB 컨테이너 안에서 계속 쌓인다.
     */
    @Test
    void 상한을_넘으면_오래된_것부터_나간다() {
        RecentCourses recent = new RecentCourses();

        for (int i = 0; i < RecentCourses.MAX_ENTRIES + 50; i++) {
            recent.get(command(i, 2, TransportMode.CAR), RecentCoursesTest::course);
        }

        assertEquals(RecentCourses.MAX_ENTRIES, recent.size(), "상한이 안 걸려 계속 쌓인다");
    }

    /** 상한을 넘겨도 <b>가장 최근 것</b>은 남아 있다 — 뒤로가기 왕복이 바로 그 경우다. */
    @Test
    void 상한을_넘어도_방금_것은_남는다() {
        RecentCourses recent = new RecentCourses();
        AtomicInteger built = new AtomicInteger();
        GenerateCourse latest = command(999_999L, 2, TransportMode.CAR);

        for (int i = 0; i < RecentCourses.MAX_ENTRIES - 1; i++) {
            recent.get(command(i, 2, TransportMode.CAR), RecentCoursesTest::course);
        }
        recent.get(latest, () -> counted(built));
        recent.get(latest, () -> counted(built));

        assertEquals(1, built.get(), "방금 만든 것이 벌써 밀려났다");
    }

    @Test
    void 들고_있는_시간은_1분이다() {
        // 이 값을 늘리려면 그 안에 무엇이 낡는지를 먼저 답해야 한다 — 코스에는 날씨·혼잡·운영시간이 섞인다.
        assertTrue(RecentCourses.TTL.toSeconds() == 60, "TTL 이 바뀌었다면 낡는 값들을 다시 따져 봤는가");
    }

    private static GeneratedCourse counted(AtomicInteger built) {
        built.incrementAndGet();
        return course();
    }

    /** 이 테스트는 코스의 내용을 보지 않는다 — 같은 것을 돌려주는지만 본다. */
    private static GeneratedCourse course() {
        return GeneratedCourse.of(null, List.of(), "테스트지역");
    }

    private static GenerateCourse command(long regionId, int travelDays, TransportMode transport) {
        return GenerateCourse.builder()
                .regionId(regionId)
                .travelDays(travelDays)
                .density(Density.RELAXED)
                .transport(transport)
                .originLat(37.50)
                .originLng(127.00)
                .travelDate(TRAVEL_DATE)
                .startDayLeave(StartDayLeave.DEFAULT)
                .excludePoiContentIds(Set.of())
                .build();
    }
}
