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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
        @Schema(
                        description = "여행 시작일. 넣어두면 코스 확정 시 연차 차감 일수를 서버가 이 날짜로 계산한다. 없으면 차감을 요청할 수 없다.",
                        example = "2026-08-14",
                        nullable = true)
                LocalDate travelDate,
        @Schema(
                        description =
                                "여행 기간(1~3). 생성 응답의 travelDays 를 그대로 돌려주면 된다. "
                                        + "없으면 담아 보낸 날 수로 본다 — 첫날이 이동뿐이라 일정에서 빠진 코스는 "
                                        + "이 값을 넣어야 종료일·연차 차감이 맞는다.",
                        example = "3",
                        nullable = true)
                @Min(1)
                @Max(Course.MAX_TRAVEL_DAYS)
                Integer travelDays,
        @NotEmpty @Valid List<Day> days) {

    /**
     * 게스트 소유의 도메인 코스로 변환한다. Bean Validation 이 못 잡는 도메인 불변식(일차·슬롯 순서 연속성, 게스트 ID 규칙 등)은
     * 도메인 팩토리가 던지고, 여기서 계약 예외(400)로 번역한다 — 입력 경계가 계약 검증을 소유하므로 이 매핑에서 400 을 확정한다.
     */
    public Course toCourse(String guestId) {
        try {
            List<DaySchedule> schedules =
                    days.stream().map(day -> day.toSchedule(travelDate)).toList();
            // 기간을 안 보낸 클라이언트는 담아 보낸 날 수로 본다 — 이 필드가 생기기 전과 같은 동작이라
            // 기존 연동이 깨지지 않는다. 그 경우 첫날이 빠진 코스는 종료일이 하루 이른 채로 남는다(#164).
            int span = travelDays != null ? travelDays : schedules.size();
            return Course.ownedBy(guestId, regionId, density, transport, schedules, travelDate, span);
        } catch (IllegalArgumentException e) {
            throw ItineraryException.invalidCourse();
        }
    }

    /**
     * @param day 며칠째(1부터) — 화면에 보이는 번호
     * @param date 그 날의 실제 날짜. 생성 응답의 {@code date} 를 그대로 돌려주면 된다(없으면 null)
     * @param items 그 날의 방문 순서대로의 장소
     */
    public record Day(
            @NotNull @Min(1) Integer day,
            @Schema(
                            description = "그 날의 날짜. 생성 응답의 date 를 그대로 돌려주면 된다. "
                                    + "없으면 며칠째를 그대로 달력 위치로 본다 — 첫날이 이동뿐이라 일정에서 빠진 코스는 "
                                    + "이 값을 넣어야 저장 후 날짜가 생성 때와 같다.",
                            example = "2026-09-12",
                            nullable = true)
                    LocalDate date,
            @NotEmpty @Valid List<Item> items) {

        /**
         * 도메인 하루로 바꾼다 — <b>달력 위치(오프셋)</b>를 여기서 정한다.
         *
         * <p>날짜를 받으면 여행 시작일로부터의 간격이 곧 오프셋이다. 못 받으면 {@code day - 1} 로 본다 —
         * 이 필드가 생기기 전과 같은 동작이라 기존 연동이 깨지지 않는다. 다만 그 경우 첫날이 빠진 코스는
         * 저장 후 날짜가 하루 당겨진 채로 남는다(이 이슈가 고치려는 것).
         */
        DaySchedule toSchedule(LocalDate travelDate) {
            List<Slot> slots = items.stream().map(Item::toSlot).toList();
            if (date != null && travelDate == null) {
                // 기준점이 없으면 날짜를 오프셋으로 옮길 수 없다. 조용히 무시하면 클라이언트가 지정한
                // 날짜와 다른 값이 저장되고, 왜 달라졌는지 알 방법이 없다 — 모순된 요청이므로 거절한다.
                throw new IllegalArgumentException("여행 시작일 없이 Day 날짜만 보낼 수 없습니다: " + date);
            }
            if (date == null) {
                return DaySchedule.of(day, slots);
            }
            long offset = ChronoUnit.DAYS.between(travelDate, date);
            if (offset < 0 || offset > Integer.MAX_VALUE) {
                // 여행 시작일보다 앞선 날짜다 — 기간 초과는 Course 가 잡지만 음수는 여기서 끊는다.
                throw new IllegalArgumentException("여행 시작일보다 앞선 날짜입니다: " + date);
            }
            return DaySchedule.of(day, (int) offset, slots);
        }
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
