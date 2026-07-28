package com.offway.core.itinerary.controller.dto;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.itinerary.service.dto.GeneratedCourse;
import com.offway.core.policy.domain.PolicyType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 코스 생성 응답 — API 계약. 날짜별 타임라인(Day 탭)과 지도 핀 좌표·이동시간, 적용 혜택을 담는다.
 *
 * <p>POI 이미지·운영시간은 장소 상세({@code GET /pois/{id}}, #32)에서 받는다. 혜택은 정책 매칭 결과라 응답 시점 값이다.
 *
 * @param regionId 코스 지역
 * @param travelDays 여행 일수
 * @param density 일정 밀도(PACKED·RELAXED)
 * @param days 날짜별 일정
 * @param benefits 적용 혜택 뱃지
 */
public record CourseResponse(
        Long courseId, long regionId, int travelDays, String density, List<Day> days, List<Benefit> benefits) {

    public static CourseResponse from(GeneratedCourse generated) {
        Course course = generated.course();
        return new CourseResponse(
                course.getId(), // 저장된 코스만 값, 생성만 된 코스는 null
                course.getRegionId(),
                course.getTravelDays(),
                course.getDensity().name(),
                course.getDays().stream().map(Day::from).toList(),
                generated.benefits().stream().map(Benefit::from).toList());
    }

    /**
     * @param day 며칠째(1부터)
     * @param items 그 날의 방문 순서대로의 장소
     */
    public record Day(int day, List<Item> items) {

        static Day from(DaySchedule schedule) {
            return new Day(schedule.getDayNumber(), schedule.getSlots().stream().map(Item::from).toList());
        }
    }

    /**
     * @param order 하루 안 방문 순서
     * @param timeOfDay 시간대(MORNING·LUNCH·AFTERNOON·DINNER)
     * @param kind 장소 종류(SIGHT·FOOD·STAY)
     * @param poiContentId TourAPI 콘텐츠 ID(장소 상세 조회용)
     * @param title 장소명
     * @param lat 위도(지도 핀)
     * @param lng 경도
     * @param travelMinutes 직전 장소에서의 이동시간(분, 첫 장소는 0)
     */
    public record Item(
            int order,
            String timeOfDay,
            String kind,
            String poiContentId,
            @Schema(example = "완도타워 전망대") String title,
            double lat,
            double lng,
            int travelMinutes) {

        static Item from(Slot slot) {
            return new Item(
                    slot.getOrderInDay(),
                    slot.getTimeOfDay().name(),
                    slot.getKind().name(),
                    slot.getPoiContentId(),
                    slot.getTitle(),
                    slot.getLat(),
                    slot.getLng(),
                    slot.getTravelMinutesFromPrev());
        }
    }

    /**
     * @param policyId 정책 ID
     * @param type 정책 분류
     * @param text 뱃지 문구
     */
    public record Benefit(long policyId, PolicyType type, String text) {

        static Benefit from(GeneratedCourse.Benefit benefit) {
            return new Benefit(benefit.policyId(), benefit.type(), benefit.text());
        }
    }
}
