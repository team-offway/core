package com.offway.core.itinerary.controller.dto;

import com.offway.core.common.logging.LogSummary;
import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.itinerary.service.dto.GeneratedCourse;
import com.offway.core.policy.domain.PolicyType;
import com.offway.core.transport.service.dto.TrainAccess;
import com.offway.core.weather.domain.AirQuality;
import com.offway.core.weather.domain.DailyWeather;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.stream.IntStream;
import java.util.List;

/**
 * 코스 생성 응답 — API 계약. 날짜별 타임라인(Day 탭)과 지도 핀 좌표·이동시간, 적용 혜택·여행 날씨를 담는다.
 *
 * <p>슬롯은 타임라인 인라인 렌더용 표시 정보(이미지·주소·카테고리·추천 한 줄)를 함께 담는다. 운영시간·상세 소개 등 더 깊은
 * 정보는 장소 상세({@code GET /pois/{id}})에서 받는다. 혜택은 정책 매칭 결과라 응답 시점 값이다.
 *
 * @param regionId 코스 지역
 * @param travelDays 여행 일수
 * @param density 일정 밀도(PACKED·RELAXED)
 * @param days 날짜별 일정
 * @param benefits 적용 혜택 뱃지
 * @param airQuality 코스 지역의 <b>실시간</b> 대기질. <b>오늘 여행 중인 코스에만</b> 실린다(오늘 출발이거나
 *     여행 중). 예보가 아니라 조회 시점의 측정치라, 다음 주 코스에 붙이면 여행일 공기질로 오해된다.
 *     화면에는 "현재 기준" 임이 드러나게 표기하는 편이 좋다. 그 밖에는 필드가 없다
 * @param trainAccess 대중교통 코스일 때 출발지→지역 열차 접근. <b>null 은 오류가 아니다</b> — 자차 코스이거나,
 *     출발지 없이 저장된 코스(저장 요청에 {@code originLat}·{@code originLng} 를 안 보낸 경우)다. 저장 코스도
 *     출발지가 있으면 조회 시점에 다시 계산해 채운다(#187)
 */
/**
 * 값이 없는 선택 필드는 내려보내지 않는다.
 *
 * <p>인허가 데이터로 채운 슬롯은 사진·소개가 없어 매번 {@code null} 이 실린다. 슬롯이 스무 개면 그만큼
 * 빈 칸이 오가는데, 클라이언트 입장에서 "필드가 없다" 와 "null 이다" 는 어차피 같은 분기다.
 *
 * <p>전역 설정(spring.jackson.default-property-inclusion)으로 켜지 않는다 — 응답 래퍼의
 * {@code data}·{@code pageResponse} 는 <b>null 로 나가는 것이 계약</b>이라(exception-and-response 규약)
 * 전역으로 걸면 그 약속이 깨진다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CourseResponse(
        Long courseId,
        long regionId,
        int travelDays,
        @Schema(description = "여행 시작일 (저장 시 넣지 않았으면 null)", example = "2026-08-14", nullable = true)
                LocalDate travelDate,
        String density,
        List<Day> days,
        List<Benefit> benefits,
        @Schema(description = "대중교통 코스의 출발지→지역 열차 접근 (자차·저장 코스는 null)", nullable = true)
                TrainAccessResponse trainAccess,
        @Schema(
                        description = "코스 지역의 실시간 대기질. 오늘 여행 중인 코스에만 실린다 (조회 시점 측정치)",
                        nullable = true)
                AirQualityResponse airQuality,
        @Schema(
                        description = "공유 링크 토큰 (저장 응답에만 실린다). 공유 URL 은 /c/{shareToken}",
                        example = "a1B2c3D4e5F6g7H8i9J0kL",
                        nullable = true)
                String shareToken) implements LogSummary {

    /** regionId 는 요청 쿼리에 이미 있으므로 되풀이하지 않는다. */
    private static final String LOG_FORMAT = "코스 %d일 %d슬롯";

    public static CourseResponse from(GeneratedCourse generated) {
        Course course = generated.course();
        return new CourseResponse(
                course.getId(), // 저장된 코스만 값, 생성만 된 코스는 null
                course.getRegionId(),
                course.getTravelDays(),
                course.getTravelDate(),
                course.getDensity().name(),
                course.getDays().stream()
                        .map(day -> Day.from(
                                day,
                                course.getTravelDate(),
                                generated.regionName(),
                                generated.weatherByDay().get(day.getDayNumber())))
                        .toList(),
                generated.benefits().stream().map(Benefit::from).toList(),
                generated.trainAccess() == null ? null : TrainAccessResponse.from(generated.trainAccess()),
                generated.airQuality() == null ? null : AirQualityResponse.from(generated.airQuality()),
                generated.shareToken());
    }

    /**
     * 공유 링크로 여는 사람에게 주는 응답(#143) — <b>내부 식별자를 걷어낸다</b>.
     *
     * <p>{@code courseId} 와 {@code shareToken} 을 뺀다. 링크를 받은 사람은 이 코스를 수정·삭제할 수 없으므로
     * 내부 순번을 알 이유가 없고, 알려주면 다른 경로를 두드려 볼 단서만 준다. 토큰은 이미 URL 에 있어
     * 본문에 되돌려줄 이유가 없다.
     *
     * <p>{@code @JsonInclude(NON_NULL)} 이라 두 필드는 응답에서 <b>키 자체가 사라진다</b>.
     */
    public static CourseResponse publicView(GeneratedCourse generated) {
        CourseResponse owned = from(generated);
        return new CourseResponse(
                null, // courseId — 내부 순번을 공개하지 않는다
                owned.regionId(),
                owned.travelDays(),
                owned.travelDate(),
                owned.density(),
                owned.days(),
                owned.benefits(),
                owned.trainAccess(),
                owned.airQuality(),
                null); // shareToken — 이미 URL 에 있다
    }

    @Override
    public String logSummary() {
        int slots = days == null
                ? 0
                : days.stream()
                        .mapToInt(day -> day.items() == null ? 0 : day.items().size())
                        .sum();
        return LOG_FORMAT.formatted(travelDays, slots);
    }

    /**
     * @param day 며칠째(1부터)
     * @param date 그날의 실제 날짜 (여행 시작일 없이 저장된 코스는 null)
     * @param weather 그날의 날씨 (예보 범위 밖·조회 실패면 null)
     * @param dayOfWeek 요일 (날짜가 없으면 null)
     * @param items 그 날의 방문 순서대로의 장소
     */
    /**
     * 값이 없는 선택 필드는 내려보내지 않는다.
     *
     * <p>인허가 데이터로 채운 슬롯은 사진·소개가 없어 매번 {@code null} 이 실린다. 슬롯이 스무 개면 그만큼
     * 빈 칸이 오가는데, 클라이언트 입장에서 "필드가 없다" 와 "null 이다" 는 어차피 같은 분기다.
     *
     * <p>전역 설정(spring.jackson.default-property-inclusion)으로 켜지 않는다 — 응답 래퍼의
     * {@code data}·{@code pageResponse} 는 <b>null 로 나가는 것이 계약</b>이라(exception-and-response 규약)
     * 전역으로 걸면 그 약속이 깨진다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Day(
            int day,
            @Schema(example = "2026-07-26", nullable = true) LocalDate date,
            @Schema(description = "요일", example = "SATURDAY", nullable = true) String dayOfWeek,
            @Schema(description = "그날의 날씨 (예보 없으면 null)", nullable = true) Weather weather,
            List<Item> items) {

        static Day from(
                DaySchedule schedule, LocalDate travelDate, String regionName, DailyWeather weather) {
            // 표시 번호가 아니라 달력 오프셋으로 센다 — 첫날이 빠진 코스에서 하루 앞당겨지지 않게(#159).
            LocalDate date = travelDate == null ? null : travelDate.plusDays(schedule.getDayOffset());
            List<Slot> slots = schedule.getSlots();
            List<Item> items = IntStream.range(0, slots.size())
                    .mapToObj(i -> Item.from(slots.get(i), schedule.distanceFromPrevMeters(i), regionName))
                    .toList();
            return new Day(
                    schedule.getDayNumber(),
                    date,
                    date == null ? null : date.getDayOfWeek().name(),
                    weather == null ? null : Weather.from(weather),
                    items);
        }
    }

    /**
     * @param order 하루 안 방문 순서
     * @param timeOfDay 시간대(MORNING·LUNCH·AFTERNOON·DINNER)
     * @param kind 장소 종류(SIGHT·FOOD·STAY)
     * @param categoryLabel 종류 한글 라벨(관광·맛집·숙박) — 카드 표시용
     * @param poiContentId TourAPI 콘텐츠 ID(장소 상세 조회용)
     * @param title 장소명
     * @param imageUrl 대표 이미지(없으면 null)
     * @param address 주소(없으면 null)
     * @param catchphrase 추천 한 줄 문구(구석구석 캐치프레이즈, 없으면 null)
     * @param lat 위도(지도 핀)
     * @param lng 경도
     * @param travelMinutes 직전 장소에서의 이동시간(분, 첫 장소는 0)
     */
    /**
     * 값이 없는 선택 필드는 내려보내지 않는다.
     *
     * <p>인허가 데이터로 채운 슬롯은 사진·소개가 없어 매번 {@code null} 이 실린다. 슬롯이 스무 개면 그만큼
     * 빈 칸이 오가는데, 클라이언트 입장에서 "필드가 없다" 와 "null 이다" 는 어차피 같은 분기다.
     *
     * <p>전역 설정(spring.jackson.default-property-inclusion)으로 켜지 않는다 — 응답 래퍼의
     * {@code data}·{@code pageResponse} 는 <b>null 로 나가는 것이 계약</b>이라(exception-and-response 규약)
     * 전역으로 걸면 그 약속이 깨진다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(
            int order,
            String timeOfDay,
            String kind,
            @Schema(example = "관광") String categoryLabel,
            String poiContentId,
            @Schema(example = "완도타워 전망대") String title,
            @Schema(nullable = true) String imageUrl,
            @Schema(example = "전남 완도군", nullable = true) String address,
            @Schema(example = "바다 위에 뜬 낭만, 완도의 랜드마크", nullable = true) String catchphrase,
            double lat,
            double lng,
            int travelMinutes,
            @Schema(description = "앞 장소와의 직선거리(m). 첫 장소는 null", example = "8300", nullable = true)
                    Integer distanceFromPrevMeters,
            @Schema(description = "코스 지역의 짧은 이름", example = "정선군", nullable = true) String regionName) {

        static Item from(Slot slot, Integer distanceFromPrevMeters, String regionName) {
            return new Item(
                    slot.getOrderInDay(),
                    slot.getTimeOfDay().name(),
                    slot.getKind().name(),
                    slot.getKind().label(),
                    slot.getPoiContentId(),
                    slot.getTitle(),
                    slot.getImageUrl(),
                    slot.getAddress(),
                    slot.getCatchphrase(),
                    slot.getLat(),
                    slot.getLng(),
                    slot.getTravelMinutesFromPrev(),
                    distanceFromPrevMeters,
                    regionName);
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
    /**
     * 값이 없는 선택 필드는 내려보내지 않는다.
     *
     * <p>인허가 데이터로 채운 슬롯은 사진·소개가 없어 매번 {@code null} 이 실린다. 슬롯이 스무 개면 그만큼
     * 빈 칸이 오가는데, 클라이언트 입장에서 "필드가 없다" 와 "null 이다" 는 어차피 같은 분기다.
     *
     * <p>전역 설정(spring.jackson.default-property-inclusion)으로 켜지 않는다 — 응답 래퍼의
     * {@code data}·{@code pageResponse} 는 <b>null 로 나가는 것이 계약</b>이라(exception-and-response 규약)
     * 전역으로 걸면 그 약속이 깨진다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
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
     * 코스 지역의 실시간 대기질 — 시도 단위 발표라 코스에 하나만 붙는다.
     *
     * <p><b>예보가 아니다.</b> 조회 시점의 측정치라 오늘 여행 중인 코스에만 실린다. 홈 카드에 붙어 있던 것을
     * 여기로 옮겼다 — 다음 주 여행지를 고르는 자리에서 지금 공기질은 판단 근거가 못 되고, 그것 때문에 홈이
     * 외부 호출을 물고 느려졌다.
     *
     * @param pm10 미세먼지 평균 (㎍/㎥, 없으면 null)
     * @param pm25 초미세먼지 평균 (㎍/㎥, 없으면 null)
     * @param grade 통합대기환경 등급 문구 (좋음·보통·나쁨·매우나쁨·정보없음)
     */
    public record AirQualityResponse(
            @Schema(example = "45", nullable = true) Integer pm10,
            @Schema(example = "23", nullable = true) Integer pm25,
            @Schema(example = "보통") String grade) {

        static AirQualityResponse from(AirQuality air) {
            return new AirQualityResponse(air.pm10(), air.pm25(), air.grade().label());
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
