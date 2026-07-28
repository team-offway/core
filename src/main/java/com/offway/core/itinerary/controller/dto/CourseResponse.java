package com.offway.core.itinerary.controller.dto;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.itinerary.service.dto.GeneratedCourse;
import com.offway.core.policy.domain.PolicyType;
import com.offway.core.transport.service.dto.TrainAccess;
import com.offway.core.weather.domain.DailyWeather;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 코스 생성 응답 — API 계약. 날짜별 타임라인(Day 탭)과 지도 핀 좌표·이동시간, 적용 혜택·여행 날씨를 담는다.
 *
 * <p>POI 이미지·운영시간은 장소 상세({@code GET /pois/{id}}, #32)에서 받는다. 혜택은 정책 매칭 결과라 응답 시점 값이다.
 *
 * @param regionId 코스 지역
 * @param travelDays 여행 일수
 * @param density 일정 밀도(PACKED·RELAXED)
 * @param days 날짜별 일정
 * @param benefits 적용 혜택 뱃지
 * @param weather 여행 날짜의 코스 지역 날씨(생성 시점만 — 저장 코스·예보범위 밖·미조회면 null)
 * @param trainAccess 대중교통 코스일 때 출발지→지역 열차 접근(자차·저장 코스는 null)
 */
public record CourseResponse(
        Long courseId,
        long regionId,
        int travelDays,
        String density,
        List<Day> days,
        List<Benefit> benefits,
        @Schema(description = "여행 날짜의 코스 지역 날씨 (없으면 null)", nullable = true) Weather weather,
        @Schema(description = "대중교통 코스의 출발지→지역 열차 접근 (자차·저장 코스는 null)", nullable = true)
                TrainAccessResponse trainAccess) {

    public static CourseResponse from(GeneratedCourse generated) {
        Course course = generated.course();
        return new CourseResponse(
                course.getId(), // 저장된 코스만 값, 생성만 된 코스는 null
                course.getRegionId(),
                course.getTravelDays(),
                course.getDensity().name(),
                course.getDays().stream().map(Day::from).toList(),
                generated.benefits().stream().map(Benefit::from).toList(),
                generated.weather() == null ? null : Weather.from(generated.weather()),
                generated.trainAccess() == null ? null : TrainAccessResponse.from(generated.trainAccess()));
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

    /**
     * @param date 예보 날짜
     * @param minTemp 최저기온(℃, 없으면 null)
     * @param maxTemp 최고기온(℃, 없으면 null)
     * @param sky 하늘 상태 문구(맑음·구름많음·흐림·정보 없음)
     * @param rainProbability 강수확률 최대(%, 없으면 null)
     */
    public record Weather(
            LocalDate date,
            @Schema(example = "18", nullable = true) Integer minTemp,
            @Schema(example = "27", nullable = true) Integer maxTemp,
            @Schema(example = "맑음") String sky,
            @Schema(example = "20", nullable = true) Integer rainProbability) {

        static Weather from(DailyWeather weather) {
            return new Weather(
                    weather.date(),
                    weather.minTemp(),
                    weather.maxTemp(),
                    weather.sky().label(),
                    weather.rainProbability());
        }
    }

    /**
     * 출발지→지역 열차 접근.
     *
     * @param status AVAILABLE(운행 있음) · NO_STATION(역 없음, 열차로 못 감) · NO_SERVICE_ON_DATE(그날 미운행) · UNAVAILABLE(조회 실패)
     * @param fromStation 출발역명(없으면 null)
     * @param toStation 도착역명(없으면 null)
     * @param trainType 가장 빠른 열차 등급(AVAILABLE 일 때만, 예: KTX)
     * @param durationMinutes 소요시간(분, AVAILABLE 일 때만)
     */
    public record TrainAccessResponse(
            @Schema(example = "AVAILABLE") String status,
            @Schema(example = "서울", nullable = true) String fromStation,
            @Schema(example = "정선", nullable = true) String toStation,
            @Schema(example = "KTX", nullable = true) String trainType,
            @Schema(example = "150", nullable = true) Integer durationMinutes) {

        static TrainAccessResponse from(TrainAccess access) {
            boolean hasTrain = access.fastest() != null;
            return new TrainAccessResponse(
                    access.status().name(),
                    access.fromStation(),
                    access.toStation(),
                    hasTrain ? access.fastest().trainType() : null,
                    hasTrain ? access.fastest().durationMinutes() : null);
        }
    }
}
