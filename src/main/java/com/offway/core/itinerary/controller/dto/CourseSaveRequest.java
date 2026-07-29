package com.offway.core.itinerary.controller.dto;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.Density;
import com.offway.core.itinerary.domain.ItineraryException;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.itinerary.domain.SlotKind;
import com.offway.core.itinerary.domain.TimeOfDay;
import com.offway.core.transport.domain.TransportMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

/**
 * 코스 저장 요청 — API 계약. 생성({@code POST /courses/generate})으로 받은 코스를 그대로 담아 "내 코스"로 저장한다.
 *
 * @param regionId 코스 지역
 * @param density 일정 밀도
 * @param transport 이동수단
 * @param days 날짜별 일정(최소 1일)
 */
public record CourseSaveRequest(
        @Schema(example = "16", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Positive Long regionId,
        @Schema(example = "PACKED", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Density density,
        @Schema(example = "CAR", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull TransportMode transport,
        @NotEmpty @Valid List<Day> days) {

    /**
     * 게스트 소유의 도메인 코스로 변환한다. Bean Validation 이 못 잡는 도메인 불변식(일차·슬롯 순서 연속성, 게스트 ID 규칙 등)은
     * 도메인 팩토리가 던지고, 여기서 계약 예외(400)로 번역한다 — 입력 경계가 계약 검증을 소유하므로 이 매핑에서 400 을 확정한다.
     */
    public Course toCourse(String guestId) {
        try {
            List<DaySchedule> schedules = days.stream()
                    .map(day -> DaySchedule.of(day.day(), day.items().stream().map(Item::toSlot).toList()))
                    .toList();
            return Course.ownedBy(guestId, regionId, density, transport, schedules);
        } catch (IllegalArgumentException e) {
            throw ItineraryException.invalidCourse();
        }
    }

    /**
     * @param day 며칠째(1부터)
     * @param items 그 날의 방문 순서대로의 장소
     */
    public record Day(
            @NotNull @Min(1) Integer day,
            @NotEmpty @Valid List<Item> items) {
    }

    /**
     * @param order 하루 안 방문 순서(1부터)
     * @param timeOfDay 시간대
     * @param kind 장소 종류
     * @param poiContentId TourAPI 콘텐츠 ID
     * @param title 장소명
     * @param imageUrl 대표 이미지(생성 응답 값 그대로 — 없으면 null)
     * @param address 주소(없으면 null)
     * @param catchphrase 추천 한 줄 문구(없으면 null)
     * @param lat 위도
     * @param lng 경도
     * @param travelMinutes 직전 장소에서 이동시간(첫 장소 0)
     */
    public record Item(
            @NotNull @Min(1) Integer order,
            @NotNull TimeOfDay timeOfDay,
            @NotNull SlotKind kind,
            @NotBlank String poiContentId,
            @NotBlank String title,
            @Schema(nullable = true) String imageUrl,
            @Schema(nullable = true) String address,
            @Schema(nullable = true) String catchphrase,
            @NotNull Double lat,
            @NotNull Double lng,
            @NotNull @Min(0) Integer travelMinutes) {

        Slot toSlot() {
            // 표시 정보(이미지·주소·추천 한 줄)를 함께 영속해 저장 코스도 TourAPI 재조회 없이 그린다.
            return Slot.of(order, timeOfDay, kind, poiContentId, title, lat, lng, travelMinutes,
                    imageUrl, address, catchphrase);
        }
    }
}
