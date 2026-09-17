package com.offway.core.itinerary.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.itinerary.domain.Density;
import com.offway.core.itinerary.service.dto.GenerateCourse;
import com.offway.core.itinerary.service.dto.GeneratedCourse;
import com.offway.core.leave.domain.StartDayLeave;
import com.offway.core.transport.domain.TransportMode;
import com.offway.core.trip.infrastructure.tour.StubTourApiClient;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourPoi;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiResult;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * <b>서비스가 실제로 캐시를 지나는가</b>(#584 · PR #588 리뷰).
 *
 * <h2>왜 따로 필요한가</h2>
 *
 * <p>{@link RecentCoursesTest} 는 캐시 <b>자체</b>를 본다. 그것만으로는
 * {@code CourseGenerationService} 에서 {@code recentCourses.get(...)} 을 통째로 지워도, 또는
 * 후보 수집을 supplier <b>밖</b>으로 꺼내도 <b>아무 테스트도 안 깨진다.</b> 그러면 후보 수집(TourAPI
 * 3콜)이 재사용에서 빠져나와 캐시가 반만 듣는다 — <b>#584 가 막으려던 비용의 절반이 그쪽에 있다.</b>
 *
 * <p>여기서 보는 것은 그 배선 하나다 — 같은 커맨드로 두 번 부르면 <b>TourAPI 가 다시 안 나가는가</b>.
 *
 * <h2>이 클래스만 캐시를 켠다</h2>
 *
 * <p>다른 통합 테스트는 캐시를 끈 채 돈다({@code src/test/resources/application-local.properties}) —
 * 그쪽은 같은 커맨드로 stub 만 갈아 가며 시나리오를 도는데, 켜 두면 앞 시나리오의 코스가 새어 든다.
 *
 * <p>그래서 여기서만 {@code @Primary} 로 켠 것을 얹는다. 생성자에 참을 직접 넣으므로 프로퍼티와
 * 무관하다. 컨텍스트가 하나 더 뜨지만, <b>끈 채로는 배선을 검증할 방법이 없다.</b>
 */
@SpringBootTest
class RecentCourseReuseIntegrationTest {

    /** 시드된 인구감소지역(부산 동구) — 다른 코스 생성 통합 테스트와 같은 값이다. */
    private static final long SEEDED_REGION_ID = 1L;

    /** 고정 날짜 — 결과가 실행일에 흔들리지 않게. */
    private static final LocalDate TRAVEL_DATE = LocalDate.of(2026, 7, 15);

    /**
     * <b>테스트마다 다른 출발지를 쓴다</b> — 그래야 캐시 키가 안 겹친다.
     *
     * <p>캐시는 이 컨텍스트에 <b>하나뿐이고</b>, {@code resetAreaCallCount()} 는 호출 횟수만 0 으로
     * 되돌릴 뿐 캐시를 비우지 않는다. 두 테스트가 같은 커맨드를 쓰면 TTL 1분 안에 둘 다 도는 동안
     * 앞 테스트가 채운 것이 뒤 테스트의 <b>첫</b> 호출에 적중한다. 그러면 "첫 생성이 실제로 불렀나"
     * 가 0 이 되어 <b>실행 순서에 따라 깨진다.</b>
     *
     * <p>캐시를 비우는 문은 안 냈다 — 테스트 편의를 위해 운영 코드에 구멍을 내는 대신, 겹치지 않는
     * 입력을 고르면 되는 일이다. 출발지 좌표는 커맨드의 일부라 그것만 달리하면 키가 갈린다.
     */
    private static final double ORIGIN_REUSE = 129.03;

    private static final double ORIGIN_DISTINCT = 129.40;

    @Autowired
    private CourseGenerationService courseGenerationService;

    @Autowired
    private StubTourApiClient tourApiClient;

    @TestConfiguration
    static class CacheOnConfig {

        /** 이 컨텍스트에서만 캐시를 켠다 — 프로퍼티(테스트에서 꺼져 있다)를 타지 않는다. */
        @Bean
        @Primary
        RecentCourses cacheOnRecentCourses() {
            return new RecentCourses(true);
        }

        @Bean
        @Primary
        TourApiClient stubTourApiClient() {
            return new StubTourApiClient();
        }
    }

    @Test
    void 같은_요청을_두_번_하면_후보를_다시_모으지_않는다() {
        tourApiClient.respond(RecentCourseReuseIntegrationTest::richPois);
        GenerateCourse command = command(2, ORIGIN_REUSE);
        tourApiClient.resetAreaCallCount();

        GeneratedCourse first = courseGenerationService.generate(command);
        int afterFirst = tourApiClient.areaCallCount();
        GeneratedCourse second = courseGenerationService.generate(command);

        // 이 단언이 없으면 stub 이 한 번도 안 불린 채로 뒤의 "안 늘었다" 가 통과한다.
        assertTrue(afterFirst > 0, "첫 생성이 TourAPI 를 안 불렀다 — 이 테스트가 아무것도 안 보고 있다");
        assertEquals(afterFirst, tourApiClient.areaCallCount(),
                "같은 요청인데 후보를 다시 모았다 — 수집이 캐시 밖에 있다");
        assertSame(first, second, "같은 요청에 새 코스를 만들어 줬다 — 서비스가 캐시를 안 지난다");
    }

    /** 뒤집힌 쪽 — 캐시가 서로 다른 요청을 한 덩어리로 뭉개지 않는지. */
    @Test
    void 조건이_다르면_후보를_다시_모은다() {
        tourApiClient.respond(RecentCourseReuseIntegrationTest::richPois);
        courseGenerationService.generate(command(2, ORIGIN_DISTINCT));
        int afterFirst = tourApiClient.areaCallCount();

        courseGenerationService.generate(command(1, ORIGIN_DISTINCT));

        assertTrue(tourApiClient.areaCallCount() > afterFirst,
                "일정이 다른 요청인데 앞 코스를 돌려줬다 — 키가 커맨드를 다 안 보고 있다");
    }

    private static GenerateCourse command(int travelDays, double originLng) {
        return GenerateCourse.builder()
                .regionId(SEEDED_REGION_ID)
                .travelDays(travelDays)
                .density(Density.PACKED)
                .transport(TransportMode.CAR)
                .originLat(35.10)
                .originLng(originLng)
                .travelDate(TRAVEL_DATE)
                .startDayLeave(StartDayLeave.FULL_DAY)
                .build();
    }

    /** 볼거리·맛집·숙박이 넉넉한 지역 콘텐츠(부산 인근 좌표). */
    private static TourPoiResult richPois() {
        List<TourPoi> items = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            items.add(poi("s" + i, 12, 35.10 + i * 0.01, 129.03 + i * 0.01));
        }
        items.add(poi("f0", 39, 35.11, 129.04));
        items.add(poi("f1", 39, 35.12, 129.05));
        items.add(poi("f2", 39, 35.13, 129.06));
        items.add(poi("st0", 32, 35.10, 129.03));
        items.add(poi("st1", 32, 35.15, 129.08));
        return new TourPoiResult(items, items.size());
    }

    /** 콘텐츠 타입에 맞는 대분류를 함께 준다 — 풀을 가르는 기준이 대분류다. */
    private static TourPoi poi(String id, int contentTypeId, double lat, double lng) {
        return new TourPoi(id, contentTypeId, lclsOf(contentTypeId), "장소" + id, "부산 동구", lat, lng,
                "http://img/" + id + ".jpg", null, null);
    }

    private static String lclsOf(int contentTypeId) {
        return switch (contentTypeId) {
            case 39 -> "FD";
            case 32 -> "AC";
            default -> "NA";
        };
    }
}
