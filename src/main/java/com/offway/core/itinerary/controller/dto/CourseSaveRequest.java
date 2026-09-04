package com.offway.core.itinerary.controller.dto;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.leave.domain.StartDayLeave;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.Density;
import com.offway.core.itinerary.domain.ItineraryException;
import com.offway.core.itinerary.domain.Origin;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.itinerary.domain.SlotDisplay;
import com.offway.core.itinerary.domain.SlotKind;
import com.offway.core.itinerary.domain.TimeOfDay;
import com.offway.core.common.geo.Coordinate;
import com.offway.core.transport.domain.TransportMode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * 코스 저장 요청 — API 계약. 생성({@code POST /courses/generate})으로 받은 코스를 그대로 담아 "내 코스"로 저장한다.
 *
 * @param regionId 코스 지역
 * @param density 일정 밀도
 * @param transport 이동수단
 * @param originLat 출발지 위도(대중교통 열차 접근 재계산용, 없으면 null)
 * @param originLng 출발지 경도(없으면 null)
 * @param originName 출발지 표시명(#382). 자차 코스의 "서울에서 출발" 을 그리는 값이다. 서버는 좌표를
 *     이름으로 바꾸지 못해 앱이 역지오코딩해 보낸다. <b>없어도 되고</b>, 길이가 넘거나 공백뿐이면
 *     거절하지 않고 이름만 버린다 — 곁가지 값 때문에 담기가 실패하면 주객이 뒤집힌다
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
        @Schema(
                        description = "출발지 위도. 대중교통 코스는 이 값이 있어야 저장 후에도 열차 접근이 나온다 "
                                + "(생성 요청에 보낸 값을 그대로 돌려주면 된다). 자차 코스는 필요 없다.",
                        example = "37.5665",
                        nullable = true)
                Double originLat,
        @Schema(description = "출발지 경도. originLat 와 짝.", example = "126.9780", nullable = true)
                Double originLng,
        @Schema(
                        description = "출발지 표시명. 자차 코스 상세의 '서울에서 출발' 에 쓴다. "
                                + "시·도/시·군 단위의 짧은 이름을 보낸다(접미사 없이). "
                                + "20자를 넘거나 비어 있으면 거절하지 않고 이름만 버린다.",
                        example = "서울",
                        nullable = true)
                String originName,
        @Schema(
                        description = "첫날에 쓴 연차 (생성 요청에 보낸 값을 그대로 돌려준다). 상세·날짜 수정이 "
                                + "열차 접근을 다시 계산할 때 이 값이 근거가 된다. 생략하면 FULL_DAY",
                        example = "HALF_DAY",
                        nullable = true)
                StartDayLeave startDayLeave,
        @NotEmpty List<@Valid Day> days) {

    /** 인증된 사용자 소유의 도메인 코스로 변환한다 — 예외 번역은 {@link #build} 가 소유한다. */
    public Course toCourse(UUID userId) {
        return build(origin -> Course.ownedBy(
                userId, regionId, density, transport, schedules(), travelDate, span(), origin,
                startDayLeaveOrFullDay()));
    }

    /**
     * <b>주인 없는</b> 코스로 변환한다(#261) — 담지 않고 공유 링크만 만들 때.
     *
     * <p>구성 검증은 저장과 <b>똑같다</b>. 링크로 열리는 코스가 담은 코스보다 느슨할 이유가 없고,
     * 두 경로의 규칙이 갈리면 같은 payload 가 한쪽에서만 통과한다.
     */
    public Course toSharedCourse() {
        return build(origin -> Course.sharedOnly(
                regionId, density, transport, schedules(), travelDate, span(), origin,
                startDayLeaveOrFullDay()));
    }

    /**
     * 생략된 값을 종일로 굳힌다 — 도메인이 null 을 다시 판단하지 않게 경계에서 정한다.
     *
     * <p>안 보내는 클라이언트가 지금과 같은 결과를 받아야 한다. 예전에는 저장 코스에 이 개념이 아예 없어
     * 상세가 가장 빠른 열차(아침 출발)를 전제했고, 그것이 곧 종일이다.
     */
    private StartDayLeave startDayLeaveOrFullDay() {
        return startDayLeave == null ? StartDayLeave.DEFAULT : startDayLeave;
    }

    /**
     * 출발지를 확정하고 도메인 팩토리를 부른다 — Bean Validation 이 못 잡는 도메인 불변식(일차·슬롯 순서
     * 연속성 등)은 도메인이 던지고, 여기서 계약 예외(400)로 번역한다. 입력 경계가 계약 검증을
     * 소유하므로 이 매핑에서 400 을 확정한다.
     */
    private Course build(Function<Origin, Course> factory) {
        try {
            // 출발지는 위도·경도가 함께여야 좌표가 된다. 한쪽만 오면 조용히 버리지 않고 거절한다 —
            // 클라이언트는 출발지를 보냈다고 여기는데 저장 코스에서 열차 접근이 비고, 그 이유를 알 수 없다.
            // Day 날짜(#180)에서 시작일 없이 날짜만 온 요청을 거절한 것과 같은 판단이다.
            if ((originLat == null) != (originLng == null)) {
                throw new IllegalArgumentException("출발지는 위도·경도를 함께 보내야 합니다");
            }
            // 이름은 좌표가 있을 때만 의미가 있다 — 이름만 온 것은 출발지가 아니다(#382).
            return factory.apply(
                    originLat == null ? null : Origin.of(new Coordinate(originLat, originLng), originName));
        } catch (IllegalArgumentException e) {
            throw ItineraryException.invalidCourse();
        }
    }

    private List<DaySchedule> schedules() {
        return days.stream().map(day -> day.toSchedule(travelDate)).toList();
    }

    /**
     * 여행 기간 — 기간을 안 보낸 클라이언트는 담아 보낸 날 수로 본다. 이 필드가 생기기 전과 같은 동작이라
     * 기존 연동이 깨지지 않는다. 그 경우 첫날이 빠진 코스는 종료일이 하루 이른 채로 남는다(#164).
     */
    private int span() {
        return travelDays != null ? travelDays : days.size();
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
            @NotEmpty List<@Valid Item> items) {

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
     * @param kind 칸 종류(대중교통 코스는 첫·끝 칸이 ARRIVAL·DEPARTURE)
     * @param poiContentId TourAPI 콘텐츠 ID. 교통 거점 칸은 <b>보내지 않는다</b>(#415)
     * @param title 장소명
     * @param imageUrl 대표 이미지(생성 응답 값 그대로 — 없으면 null)
     * @param address 주소(없으면 null)
     * @param catchphrase 추천 한 줄 문구(없으면 null)
     * @param tel 대표 전화(생성 응답 값 그대로 — 없으면 null)
     * @param lat 위도
     * @param lng 경도
     * @param travelMinutes 직전 장소에서 이동시간(첫 장소 0)
     */
    public record Item(
            @NotNull @Min(1) Integer order,
            @NotNull TimeOfDay timeOfDay,
            @NotNull SlotKind kind,
            @Schema(description = "장소 상세 조회 키. 교통 거점 칸(ARRIVAL·DEPARTURE)에는 보내지 않는다",
                    nullable = true) String poiContentId,
            @NotBlank String title,
            @Schema(nullable = true) String imageUrl,
            @Schema(nullable = true) String address,
            @Schema(nullable = true) String catchphrase,
            @Schema(nullable = true) String tel,
            @NotNull Double lat,
            @NotNull Double lng,
            @NotNull @Min(0) Integer travelMinutes) {

        /**
         * 장소 칸이면 장소 식별자가 있어야 한다(#415).
         *
         * <p>필드에 {@code @NotBlank} 를 걸 수 없게 됐다 — 교통 거점 칸은 없는 것이 정상이라, 종류를 봐야
         * 필수인지 정해진다. 검증을 도메인에만 맡기면 {@link Slot} 이 불변식 위반으로 던져 <b>클라이언트
         * 실수가 500 이 된다</b>. 계약 위반은 계약 자리에서 400 으로 끊는다.
         */
        @JsonIgnore
        @AssertTrue(message = "장소 칸에는 poiContentId 가 필요합니다")
        public boolean isPlaceIdPresentWhenNeeded() {
            if (kind == null || !kind.hasPlace()) {
                return true; // 종류 자체가 없으면 @NotNull 이 답한다 — 같은 잘못을 두 번 말하지 않는다
            }
            return poiContentId != null && !poiContentId.isBlank();
        }

        Slot toSlot() {
            if (kind != null && !kind.hasPlace()) {
                // 교통 거점 칸 — 역·터미널이라 장소 식별자도 표시 정보도 없다(#415). 클라이언트가 실어
                // 보내도 버린다: 남겨 두면 접두어 없는 값이 TourAPI 출처로 읽혀 응답 표기가 틀어진다.
                return Slot.transitHub(order, timeOfDay, kind, title, lat, lng, travelMinutes);
            }
            // 표시 정보(이미지·주소·추천 한 줄·전화)를 함께 영속해 저장 코스도 TourAPI 재조회 없이 그린다.
            return Slot.of(order, timeOfDay, kind, poiContentId, title, lat, lng, travelMinutes,
                    new SlotDisplay(imageUrl, address, catchphrase, tel));
        }
    }
}
