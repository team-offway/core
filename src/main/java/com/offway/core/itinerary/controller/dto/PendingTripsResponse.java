package com.offway.core.itinerary.controller.dto;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.itinerary.service.dto.PendingTrips;
import com.offway.core.common.response.Attributed;
import com.offway.core.common.response.DataSource;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 홈 진입 모달 "다녀오셨나요?" 가 그릴 것 전부(#116).
 *
 * <p>모달은 <b>이 응답 하나로 완성된다</b> — 지역명·여행 날짜·차감될 연차·지도에 찍을 좌표까지 들어 있어 카드를
 * 그리려고 코스 상세를 다시 부를 일이 없다.
 *
 * @param remainingDays 지금 남은 연차. 연차를 설정한 적이 없으면 null (모달이 "13일 → 10일" 을 못 그린다)
 * @param trips 물어볼 여행. 비어 있으면 모달을 띄우지 않는다
 */
public record PendingTripsResponse(
        @Schema(description = "지금 남은 연차 (설정한 적 없으면 null)", example = "13.0", nullable = true)
                Double remainingDays,
        List<Trip> trips) implements Attributed {

    /**
     * 깎일 연차가 <b>평일에서 공휴일을 뺀 값</b>이라 특일정보를 지난다(#399).
     *
     * <p>지도 썸네일 좌표는 코스 장소에서 오지만 이 응답에는 <b>좌표만</b> 실린다 — 어느 출처의 장소였는지
     * 가릴 식별자가 없다. 모르는 것을 지어내지 않고, 확실한 것만 적는다.
     */
    @Override
    public Set<DataSource> sources() {
        return trips.isEmpty() ? Set.of() : Set.of(DataSource.KASI);
    }

    public static PendingTripsResponse from(PendingTrips pending) {
        return new PendingTripsResponse(
                pending.remainingDays(),
                pending.trips().stream().map(course -> Trip.from(course, pending)).toList());
    }

    /**
     * @param consumedLeaveDays 다녀왔다고 하면 깎일 연차. 모달의 "연차 3일 차감"
     * @param points 지도 썸네일에 찍을 방문 순서대로의 좌표
     */
    public record Trip(
            long courseId,
            long regionId,
            @Schema(description = "지역명(시군구). 못 찾으면 null", example = "정선군", nullable = true) String regionName,
            @Schema(example = "2026-07-20") LocalDate travelDate,
            @Schema(description = "여행 종료일 (시작일 + 일수 − 1)", example = "2026-07-22") LocalDate travelEndDate,
            @Schema(example = "3") int travelDays,
            @Schema(description = "다녀왔다고 하면 깎일 연차 (평일−공휴일)", example = "3.0") Double consumedLeaveDays,
            int placeCount,
            List<Point> points) {

        static Trip from(Course course, PendingTrips pending) {
            return new Trip(
                    course.getId(),
                    course.getRegionId(),
                    pending.regionNameOf(course),
                    course.getTravelDate(),
                    course.travelEndDate(),
                    course.getTravelDays(),
                    pending.consumedLeaveDaysOf(course),
                    course.placeCount(),
                    pointsOf(course));
        }

        private static List<Point> pointsOf(Course course) {
            return course.getDays().stream()
                    .sorted(java.util.Comparator.comparingInt(DaySchedule::getDayNumber))
                    .flatMap(day -> day.getSlots().stream().map(slot -> Point.from(day.getDayNumber(), slot)))
                    .toList();
        }
    }

    /**
     * 지도 썸네일용 좌표 한 점.
     *
     * @param day 며칠째(1부터) — 날짜별로 색을 다르게 찍기 위해 필요하다
     */
    public record Point(int day, int order, double lat, double lng) {

        static Point from(int day, Slot slot) {
            return new Point(day, slot.getOrderInDay(), slot.getLat(), slot.getLng());
        }
    }
}
