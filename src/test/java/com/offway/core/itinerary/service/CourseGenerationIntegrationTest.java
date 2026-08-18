package com.offway.core.itinerary.service;

import com.offway.core.leave.domain.StartDayLeave;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.Density;
import com.offway.core.itinerary.domain.ItineraryException;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.itinerary.domain.SlotKind;
import com.offway.core.itinerary.service.dto.GenerateCourse;
import com.offway.core.itinerary.service.dto.GeneratedCourse;
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
 * 코스 생성 조율 통합 테스트 — 외부 경계(TourAPI)만 stub 하고 trip·transport·policy 는 실제 빈으로 흐름을 검증한다. regionId=1
 * 은 시드된 인구감소지역(부산 동구).
 */
@SpringBootTest
class CourseGenerationIntegrationTest {

    private static final long SEEDED_REGION_ID = 1L;

    @Autowired
    private CourseGenerationService courseGenerationService;

    @Autowired
    private StubTourApiClient tourApiClient;

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        TourApiClient stubTourApiClient() {
            return new StubTourApiClient();
        }
    }

    private static TourPoi poi(String id, int contentTypeId, double lat, double lng) {
        return new TourPoi(id, contentTypeId, "NA", "장소" + id, "부산 동구", lat, lng, "http://img/" + id + ".jpg", null);
    }

    /** 볼거리·맛집·숙박이 넉넉한 지역 콘텐츠(부산 인근 좌표). */
    private static TourPoiResult richPois() {
        List<TourPoi> items = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            items.add(poi("s" + i, 12, 35.10 + i * 0.01, 129.03 + i * 0.01)); // 관광지
        }
        items.add(poi("f0", 39, 35.11, 129.04)); // 음식점
        items.add(poi("f1", 39, 35.12, 129.05));
        items.add(poi("f2", 39, 35.13, 129.06));
        items.add(poi("f3", 39, 35.14, 129.07));
        items.add(poi("st0", 32, 35.10, 129.03)); // 숙박
        items.add(poi("st1", 32, 35.15, 129.08));
        return new TourPoiResult(items, items.size());
    }

    /**
     * 혜택이 실제로 매칭되는 고정 날짜 — 매칭 결과가 실행일에 흔들리지 않게.
     *
     * <p>이 코스의 지역은 부산 동구(시드 id 1)다. <b>반값여행 대상 16곳이 아니므로</b> 그 정책 기간을 고른다고
     * 혜택이 붙지 않는다(#217). 동구는 비수도권이라 숙박세일페스타(6/11~8/31) 대상이고, 그 기간 안의 날짜를 쓴다.
     */
    private static final LocalDate TRAVEL_DATE = LocalDate.of(2026, 7, 15);

    private static GenerateCourse command(int travelDays, Density density) {
        return GenerateCourse.first(SEEDED_REGION_ID, travelDays, density, TransportMode.CAR, 35.10, 129.03,
                TRAVEL_DATE, StartDayLeave.FULL_DAY);
    }

    @Test
    void 지역과_조건으로_날짜별_타임라인_코스를_만든다() {
        tourApiClient.respond(CourseGenerationIntegrationTest::richPois);

        GeneratedCourse result = courseGenerationService.generate(command(2, Density.PACKED));

        Course course = result.course();
        assertEquals(2, course.getTravelDays()); // 하루 일정 수에서 도출
        assertEquals(2, course.getDays().size());
        assertTrue(course.totalSlots() > 0);

        // 하루의 첫 슬롯은 이동시간 0, 순서는 1부터
        DaySchedule day1 = course.getDays().get(0);
        Slot first = day1.getSlots().get(0);
        assertEquals(1, first.getOrderInDay());
        assertEquals(0, first.getTravelMinutesFromPrev());

        // 볼거리만 아니라 끼니(음식점)·숙소가 코스에 함께 엮인다 — 풀별 타입 스코프 조회로 확실히 채워진다.
        assertTrue(day1.getSlots().stream().anyMatch(slot -> slot.getKind() == SlotKind.FOOD),
                "음식점 슬롯이 코스에 있어야 한다");
        // 멀티데이라 마지막이 아닌 날(1일차)엔 숙박 슬롯이 붙는다
        assertTrue(day1.getSlots().stream().anyMatch(slot -> slot.getKind() == SlotKind.STAY),
                "숙박 슬롯이 코스에 있어야 한다");

        // 인구감소지역이라 혜택이 매칭된다
        assertFalse(result.benefits().isEmpty());
    }

    @Test
    void 코스_슬롯에_이미지_주소_카테고리_추천문구가_실린다() {
        // 126508 은 구석구석 캐치프레이즈 CSV 에 있는 실제 contentId(경복궁) — 추천 한 줄이 실려야 한다.
        tourApiClient.respond(() -> new TourPoiResult(List.of(
                new TourPoi("126508", 12, "NA", "경복궁", "서울 종로구", 35.10, 129.03, "http://img/g.jpg", null),
                poi("s1", 12, 35.11, 129.04),
                poi("f0", 39, 35.12, 129.05)), 3));

        Course course = courseGenerationService.generate(command(1, Density.RELAXED)).course();

        Slot slot = course.getDays().stream()
                .flatMap(day -> day.getSlots().stream())
                .filter(s -> s.getPoiContentId().equals("126508"))
                .findFirst()
                .orElseThrow();
        assertEquals("http://img/g.jpg", slot.getImageUrl());
        assertEquals("서울 종로구", slot.getAddress());
        assertEquals("관광", slot.getKind().label());
        assertTrue(slot.getCatchphrase() != null && !slot.getCatchphrase().isBlank(),
                "CSV 에 있는 contentId 는 추천 문구(catchphrase)가 실려야 한다");
    }

    @Test
    void 관광지가_페이지한도를_넘어도_음식점_숙박이_코스에_들어간다() {
        // 회귀 방지 — 관광지가 후보 페이지 크기(CANDIDATE_ROWS=100)를 넘고 음식점·숙박이 그 뒤에 오는 지역.
        // 전체타입 단일 조회(기존 구현)라면 관광지가 페이지를 채워 음식점·숙박이 밀려나 코스에서 빠졌을 것.
        // 타입별 조회면 음식점(39)·숙박(32)을 각각 받아 밀려나지 않는다.
        tourApiClient.respond(() -> {
            List<TourPoi> items = new ArrayList<>();
            for (int i = 0; i < 120; i++) {
                items.add(poi("s" + i, 12, 35.10 + i * 0.001, 129.03 + i * 0.001)); // 관광지 120개 (100 초과)
            }
            items.add(poi("f0", 39, 35.11, 129.04)); // 관광지 뒤에 배치 — 전체타입 조회면 페이지에서 잘림
            items.add(poi("f1", 39, 35.12, 129.05));
            items.add(poi("st0", 32, 35.10, 129.03));
            return new TourPoiResult(items, items.size());
        });

        Course course = courseGenerationService.generate(command(2, Density.PACKED)).course();

        List<Slot> slots = course.getDays().stream().flatMap(day -> day.getSlots().stream()).toList();
        assertTrue(slots.stream().anyMatch(slot -> slot.getKind() == SlotKind.FOOD),
                "관광지가 페이지를 넘겨도 음식점 슬롯이 있어야 한다");
        assertTrue(slots.stream().anyMatch(slot -> slot.getKind() == SlotKind.STAY),
                "관광지가 페이지를 넘겨도 숙박 슬롯이 있어야 한다");
    }

    @Test
    void 당일치기는_숙박없이_하루코스를_만든다() {
        tourApiClient.respond(CourseGenerationIntegrationTest::richPois);

        Course course = courseGenerationService.generate(command(1, Density.RELAXED)).course();

        assertEquals(1, course.getTravelDays());
        assertTrue(course.getDays().get(0).getSlots().stream().noneMatch(slot -> slot.getKind() == SlotKind.STAY));
    }

    @Test
    void 배치할_장소가_없으면_코스를_만들_수_없다() {
        tourApiClient.respond(TourPoiResult::empty);

        assertThrows(ItineraryException.class, () -> courseGenerationService.generate(command(2, Density.PACKED)));
    }

    @Test
    void 관광지없이_맛집만_있으면_코스를_만들_수_없다() {
        tourApiClient.respond(() -> new TourPoiResult(
                List.of(poi("f0", 39, 35.11, 129.04), poi("f1", 39, 35.12, 129.05)), 2));

        assertThrows(ItineraryException.class, () -> courseGenerationService.generate(command(1, Density.RELAXED)));
    }
}
