package com.offway.core.itinerary.controller.dto;

import com.offway.core.common.logging.LogSummary;
import com.offway.core.curation.controller.dto.CuratedLinkResponse;
import com.offway.core.curation.domain.CuratedLink;
import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.itinerary.domain.SlotKind;
import com.offway.core.trip.domain.MapSearchLink;
import com.offway.core.itinerary.service.dto.GeneratedCourse;
import com.offway.core.itinerary.service.dto.OwnedCourse;
import lombok.Builder;
import com.offway.core.itinerary.service.dto.SlotHours;
import com.offway.core.policy.domain.PolicyType;
import com.offway.core.transport.domain.TransitMode;
import com.offway.core.transport.service.dto.RegionAccess;
import com.offway.core.weather.domain.DailyWeather;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.stream.IntStream;
import java.util.List;
import java.util.Map;

/**
 * 코스 생성 응답 — API 계약. 날짜별 타임라인(Day 탭)과 지도 핀 좌표·이동시간, 적용 혜택·여행 날씨를 담는다.
 *
 * <p>슬롯은 타임라인 인라인 렌더용 표시 정보(이미지·주소·카테고리·추천 한 줄)를 함께 담는다. 운영시간·상세 소개 등 더 깊은
 * 정보는 장소 상세({@code GET /pois/{id}})에서 받는다. 혜택은 정책 매칭 결과라 응답 시점 값이다.
 *
 * @param regionId 코스 지역
 * @param travelDays 여행 일수
 * @param density 일정 밀도(PACKED·RELAXED)
 * @param transport 이동수단(CAR·TRANSIT). 상세 화면이 자차/대중교통을 표시하는 데 쓰고, 코스를 지우고 다시 저장할 때
 *     {@code POST /courses} 의 필수값을 복원하는 근거이기도 하다(#170)
 * @param days 날짜별 일정
 * @param benefits 적용 혜택 뱃지
 * @param trainAccess 대중교통 코스일 때 출발지→지역 열차 접근. <b>null 은 오류가 아니다</b> — 자차 코스이거나,
 *     출발지 없이 저장된 코스(저장 요청에 {@code originLat}·{@code originLng} 를 안 보낸 경우)다. 저장 코스도
 *     출발지가 있으면 조회 시점에 다시 계산해 채운다(#187)
 * @param transitAccess 대중교통 코스일 때 지역 도착 정보(#97) — 무엇을 타고 어디에 내리는가. {@code trainAccess} 를
 *     대체한다. 열차가 아닌 수단(버스·여객선)으로 닿는 지역은 여기에만 값이 있다
 * @param curatedLinks 외부 페이지로 나가는 창구(#341). 코스 상세에 켜진 것만, 정렬 순으로.
 *     <b>없으면 빈 목록</b>이라 아래 NON_NULL 규칙과 무관하게 키가 항상 있다
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
@Builder(toBuilder = true)
public record CourseResponse(
        Long courseId,
        long regionId,
        int travelDays,
        @Schema(description = "여행 시작일 (저장 시 넣지 않았으면 null)", example = "2026-08-14", nullable = true)
                LocalDate travelDate,
        String density,
        @Schema(
                        description = "이동수단(CAR·TRANSIT). 상세 화면의 자차/대중교통 표시와 재저장에 쓴다",
                        example = "CAR")
                String transport,
        List<Day> days,
        List<Benefit> benefits,
        @Schema(
                        description = "대중교통 코스의 출발지→지역 열차 접근 (자차·저장 코스는 null). "
                                + "**deprecated — transitAccess 로 옮겨간다(#97)**. "
                                + "열차로 가는 코스에만 실린다. 버스·여객선으로 가는 코스는 여기가 null 이고 "
                                + "transitAccess 에만 값이 있다",
                        nullable = true)
                TrainAccessResponse trainAccess,
        @Schema(
                        description = "대중교통 코스의 지역 도착 정보(#97) — 열차·고속버스·시외버스·여객선을 한 모양으로. "
                                + "trainAccess 를 대체한다 (자차·저장 코스는 null)",
                        nullable = true)
                TransitAccessResponse transitAccess,
        @Schema(
                        description = "공유 링크 토큰. 공유 URL 은 /c/{shareToken}. "
                                + "소유자 응답(저장·상세·여행 날짜 수정)에 실리고, "
                                + "공유 링크로 여는 공개 조회에는 없다 — 이미 URL 에 있다",
                        example = "a1B2c3D4e5F6g7H8i9J0kL",
                        nullable = true)
                String shareToken,
        @Schema(
                        description = "여행 날짜를 옮겨 첫날 판단이 뒤집혔을 때만 실린다. "
                                + "TRIMMED — 도착이 늦어져 갈 수 없게 된 일정을 서버가 걷어냈다. "
                                + "FILLABLE — 도착이 빨라져 첫날을 쓸 수 있게 됐다(재생성을 권한다). "
                                + "그 밖에는 필드가 없다",
                        example = "TRIMMED",
                        nullable = true)
                String firstDayChange,
        @Schema(
                        description = "이 코스로 연차를 차감했는가(#317). 목록 요약의 같은 값이라, "
                                + "상세를 열 때 목록을 병행 조회하지 않아도 된다. "
                                + "공유 링크로 여는 공개 조회에는 없다 — 소유자에게 딸린 값이다",
                        example = "true",
                        nullable = true)
                Boolean leaveDeducted,
        @Schema(
                        description = "이 코스로 깎인 연차 일수(#317). 차감한 적 없으면 null 이다 — "
                                + "0 과 구분해야 한다. 0 은 '확정했고 깎을 평일이 없었다' 는 뜻이다",
                        example = "1.25",
                        nullable = true)
                Double consumedLeaveDays,
        List<CuratedLinkResponse> curatedLinks) implements LogSummary {

    /**
     * 예: {@code 정선군 코스 3일 26슬롯}.
     *
     * <p><b>지역명을 넣는다.</b> {@code regionId=16} 만으로는 로그를 훑을 때 어디 코스인지 알 수 없어 매번
     * 지역 표를 찾아봐야 했다. 사람이 읽는 줄이므로 사람이 아는 이름으로 쓴다.
     */
    private static final String LOG_FORMAT = "%s 코스 %d일 %d슬롯";

    /** 지역명이 없는 코스(슬롯이 비었거나 지역 조회 실패)에 쓸 대체 표기. */
    private static final String UNKNOWN_REGION = "지역미상";

    public static CourseResponse from(GeneratedCourse generated, List<CuratedLink> curatedLinks) {
        Course course = generated.course();
        return CourseResponse.builder()
                // 저장된 코스만 값, 생성만 된 코스는 null
                .courseId(course.getId())
                .regionId(course.getRegionId())
                .travelDays(course.getTravelDays())
                .travelDate(course.getTravelDate())
                .density(course.getDensity().name())
                .transport(course.getTransport().name())
                // 날짜가 바뀌는 구간의 거리는 여기서 잰다 — 좌표가 이미 있어 외부 호출이 없다(#188).
                .days(IntStream.range(0, course.getDays().size())
                        .mapToObj(i -> Day.from(
                                course.getDays().get(i),
                                course.getTravelDate(),
                                generated.regionName(),
                                generated.weatherByDay().get(course.getDays().get(i).getDayNumber()),
                                course.distanceFromPrevDayMeters(i),
                                generated.hoursByContentId(),
                                slotBenefits(generated)))
                        .toList())
                .benefits(generated.benefits().stream().map(Benefit::from).toList())
                .trainAccess(TrainAccessResponse.from(generated.trainAccess()))
                .transitAccess(TransitAccessResponse.from(generated.trainAccess()))
                .shareToken(generated.shareToken())
                .firstDayChange(generated.firstDayChange() == null ? null : generated.firstDayChange().name())
                .curatedLinks(CuratedLinkResponse.from(curatedLinks))
                // 차감 정보는 소유자 조회에서만 뜻이 있다. 생성(아직 저장 전)·공개 공유는 이 경로로 오며,
                // 그때는 값이 없다는 사실 자체가 정확한 답이다. 빌더라 적지 않으면 그대로 null 이다.
                .build();
    }

    /**
     * 소유자가 보는 상세 — 코스에 <b>차감 정보</b>를 얹는다(#317).
     *
     * <p>앱이 상세를 열 때마다 목록을 병행 조회해 요약의 차감 여부를 찾아 채우던 것을 없애려는 것이다.
     */
    public static CourseResponse from(OwnedCourse owned, List<CuratedLink> curatedLinks) {
        return from(owned.course(), curatedLinks).toBuilder()
                .leaveDeducted(owned.deducted())
                .consumedLeaveDays(owned.consumedLeaveDays())
                .build();
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
    public static CourseResponse publicView(GeneratedCourse generated, List<CuratedLink> curatedLinks) {
        // 빼는 것만 적는다 — 나머지는 그대로 흐른다. 위치 기반으로 전부 다시 나열하면 필드가 하나 끼는
        // 순간 값이 조용히 옆칸으로 밀리는데, 인접 필드가 같은 타입이면 컴파일도 통과한다.
        return from(generated, curatedLinks).toBuilder()
                .courseId(null) // 내부 순번을 공개하지 않는다
                .shareToken(null) // 이미 URL 에 있다
                .firstDayChange(null) // 날짜 수정은 소유자만 한다
                // 차감 정보는 코스가 아니라 **소유자**에게 딸린 값이다. 링크를 받은 사람에게 내리면
                // 남의 연차 사정을 알려주는 셈이라, from(GeneratedCourse) 가 이미 비우지만 여기서도 못박는다.
                .leaveDeducted(null)
                .consumedLeaveDays(null)
                .build();
    }

    @Override
    public String logSummary() {
        int slots = days == null
                ? 0
                : days.stream()
                        .mapToInt(day -> day.items() == null ? 0 : day.items().size())
                        .sum();
        return LOG_FORMAT.formatted(regionName(), travelDays, slots);
    }

    /** 지역명은 슬롯마다 붙어 있다(#141) — 첫 슬롯에서 꺼낸다. 코스 하나는 한 지역이라 어느 슬롯이든 같다. */
    private String regionName() {
        if (days == null) {
            return UNKNOWN_REGION;
        }
        return days.stream()
                .filter(day -> day.items() != null)
                .flatMap(day -> day.items().stream())
                .map(Item::regionName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse(UNKNOWN_REGION);
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
            @Schema(description = "전날 마지막 장소에서 이날 첫 장소까지 직선거리(m). 첫날은 null",
                    example = "41200", nullable = true) Integer distanceFromPrevDayMeters,
            @Schema(description = "전날 마지막 장소에서 이날 첫 장소까지 이동시간(분). 첫날은 null",
                    example = "52", nullable = true) Integer travelMinutesFromPrevDay,
            List<Item> items) {

        static Day from(
                DaySchedule schedule, LocalDate travelDate, String regionName, DailyWeather weather,
                Integer distanceFromPrevDayMeters, Map<String, SlotHours> hoursByContentId,
                Map<SlotKind, String> slotBenefits) {
            // 표시 번호가 아니라 달력 오프셋으로 센다 — 첫날이 빠진 코스에서 하루 앞당겨지지 않게(#159).
            LocalDate date = travelDate == null ? null : travelDate.plusDays(schedule.getDayOffset());
            List<Slot> slots = schedule.getSlots();
            List<Item> items = IntStream.range(0, slots.size())
                    .mapToObj(i -> Item.from(slots.get(i), schedule.distanceFromPrevMeters(i), regionName,
                            hoursByContentId.get(slots.get(i).getPoiContentId()),
                            benefitFor(slots.get(i), slotBenefits)))
                    .toList();
            return new Day(
                    schedule.getDayNumber(),
                    date,
                    date == null ? null : date.getDayOfWeek().name(),
                    weather == null ? null : Weather.from(weather),
                    distanceFromPrevDayMeters,
                    schedule.getTravelMinutesFromPrevDay(),
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
            @Schema(description = "대표 전화. 누르지 않고 바로 걸 수 있게 슬롯에 함께 싣는다(없으면 null)",
                    example = "061-550-6000", nullable = true) String tel,
            @Schema(description = "운영·영업 시간. 아직 받지 못한 장소는 null — 화면은 그 줄을 지운다",
                    example = "09:00~18:00", nullable = true) String useTime,
            @Schema(description = "휴무일. 아직 받지 못한 장소는 null", example = "매주 월요일",
                    nullable = true) String restDate,
            @Schema(description = """
                    오늘 이 장소가 여는지 — **여행일이 오늘일 때만** 실린다. 판정할 수 없으면 필드 자체가 없다.

                    `OPEN` 영업 중 · `CLOSED_TODAY` 오늘은 휴무일이에요 ·
                    `BEFORE_OPEN` 아직 문을 열기 전이에요 · `CLOSED_NOW` 오늘 운영이 끝났어요""",
                    example = "CLOSED_TODAY", nullable = true) String openingStatus,
            @Schema(description = """
                    이 장소에서 쓸 수 있는 혜택 뱃지(#140). 단정할 수 있는 것만 붙는다.

                    지금은 숙박세일페스타(숙소)뿐이다. 나머지는 프로그램 약관을 봐야 "이 장소에서 쓸 수 있나" 를
                    알 수 있는데 그 데이터가 없어 붙이지 않는다 — 근거 없이 붙이면 사용자가 못 받는 할인을
                    기대하고 간다. 지역 단위 혜택은 코스 `benefits` 로 그대로 나간다.""",
                    example = "숙박 할인", nullable = true) String benefit,
            @Schema(description = """
                    지도 검색 링크(#236). **사진이 없는 슬롯에만** 실린다.

                    숙소는 89곳 중 45곳에서 사진 있는 후보가 2곳도 안 된다 — 인허가 데이터에 사진이 없고,
                    공식 API 로 숙소 사진을 주는 곳은 유료뿐이다. 사진 없는 카드를 그대로 두는 대신
                    지도로 넘겨 위치·사진·리뷰를 거기서 보게 한다.""",
                    example = "https://map.naver.com/p/search/%EC%9D%98%EC%84%B1%EA%B5%B0+%EC%98%AC%EC%9D%B8%EB%AA%A8%ED%85%94",
                    nullable = true) String mapSearchUrl,
            double lat,
            double lng,
            int travelMinutes,
            @Schema(description = "앞 장소와의 직선거리(m). 첫 장소는 null", example = "8300", nullable = true)
                    Integer distanceFromPrevMeters,
            @Schema(description = "코스 지역의 짧은 이름", example = "정선군", nullable = true) String regionName) {

        static Item from(Slot slot, Integer distanceFromPrevMeters, String regionName,
                SlotHours hours, String benefit) {
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
                    slot.getTel(),
                    hours == null ? null : hours.useTime(),
                    hours == null ? null : hours.restDate(),
                    hours == null ? null : hours.displayStatus(),
                    benefit,
                    mapSearchUrlFor(slot),
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
     * 출발지→지역 열차 접근.
     *
     * <p><b>deprecated — {@link TransitAccessResponse} 로 옮겨간다(#97).</b> 열차 말고도 고속버스·시외버스·
     * 여객선으로 닿는 지역이 있는데 이 모양은 그것을 담지 못한다. 필드명(Station)부터 열차를 전제한다.
     *
     * <p>당분간 둘 다 내려보낸다. 이쪽은 <b>열차로 가는 코스에만</b> 실린다 — 버스·여객선으로 닿는 지역에서
     * "역 없음" 을 그대로 내리면 화면이 "갈 수 없다" 고 말하는데 실제로는 갈 수 있기 때문이다.
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

        /** 열차 결과가 아니면 {@code null} — 옛 필드는 열차만 담기로 한 계약이다. */
        static TrainAccessResponse from(RegionAccess access) {
            if (access == null || access.mode() != TransitMode.TRAIN) {
                return null;
            }
            boolean hasTrain = access.fastest() != null;
            return new TrainAccessResponse(
                    access.status().name(),
                    access.fromName(),
                    access.toName(),
                    hasTrain ? access.fastest().trainType() : null,
                    hasTrain ? access.fastest().durationMinutes() : null);
        }
    }

    /**
     * 지역 도착 정보(#97) — 무엇을 타고 어디에 내리는가. 열차·고속버스·시외버스·여객선을 <b>한 모양으로</b> 담는다.
     *
     * <p>수단이 더 늘어도 응답 모양이 안 변하게 {@code mode} 로 가른다. 필드명에서 Station 을 걷은 것도 같은
     * 이유다 — 도착 지점은 역일 수도, 터미널일 수도, 항구일 수도 있다.
     *
     * @param mode TRAIN · EXPRESS_BUS · INTERCITY_BUS · FERRY
     * @param modeLabel 화면에 그대로 쓸 한글 수단명(열차·고속버스·시외버스·여객선)
     * @param status AVAILABLE(운행 편 있음, 도착 시각까지 앎) · POINT_ONLY(도착 지점만 앎) ·
     *     NO_STATION(닿는 지점 없음) · NO_SERVICE_ON_DATE(그날 미운행) · UNAVAILABLE(조회 실패)
     * @param fromPlace 출발 지점명(역·터미널·항구, 없으면 null)
     * @param toPlace 도착 지점명(없으면 null)
     * @param vehicleType 운행 편의 등급(AVAILABLE 일 때만, 예: KTX)
     * @param durationMinutes 소요시간(분). 열차는 실제 편에서, 버스·여객선은 저장해 둔 구간 측정값에서 온다.
     *     <b>기다리는 시간은 안 들어 있다</b> — 버스·여객선은 시간표를 못 물어 다음 편까지의 대기를 모른다
     */
    public record TransitAccessResponse(
            @Schema(example = "INTERCITY_BUS") String mode,
            @Schema(example = "시외버스") String modeLabel,
            @Schema(example = "POINT_ONLY") String status,
            @Schema(example = "동서울", nullable = true) String fromPlace,
            @Schema(example = "정선", nullable = true) String toPlace,
            @Schema(example = "KTX", nullable = true) String vehicleType,
            @Schema(example = "150", nullable = true) Integer durationMinutes) {

        static TransitAccessResponse from(RegionAccess access) {
            if (access == null) {
                return null;
            }
            boolean hasLeg = access.fastest() != null;
            return new TransitAccessResponse(
                    access.mode().name(),
                    access.mode().label(),
                    access.status().name(),
                    access.fromName(),
                    access.toName(),
                    hasLeg ? access.fastest().trainType() : null,
                    // 열차는 실제 편에서, 버스·여객선은 저장해 둔 구간 측정값에서 온다(#107).
                    //
                    // Integer.valueOf 가 필요하다. 한쪽이 int(TrainLeg.durationMinutes)이고 다른 쪽이
                    // Integer 면 삼항 전체가 int 로 언박싱돼, 소요시간을 모르는 코스마다 NPE 가 난다.
                    hasLeg ? Integer.valueOf(access.fastest().durationMinutes()) : access.durationMinutes());
        }
    }

    /**
     * 슬롯 종류별 혜택 뱃지 — 코스가 이미 들고 있는 혜택에서 고른다(#140).
     *
     * <p>혜택을 새로 조회하지 않는다. 지역 혜택 매칭은 이미 끝나 있고, 여기서는 <b>그중 슬롯 종류를 단정할
     * 수 있는 것</b>만 골라 자리를 옮길 뿐이다.
     */
    private static Map<SlotKind, String> slotBenefits(GeneratedCourse generated) {
        Map<SlotKind, String> byKind = new java.util.EnumMap<>(SlotKind.class);
        generated.benefits().forEach(benefit -> benefit.type().targetScope()
                .ifPresent(scope -> byKind.putIfAbsent(SlotKind.covering(scope), benefit.text())));
        return byKind;
    }

    private static String benefitFor(Slot slot, Map<SlotKind, String> slotBenefits) {
        return slotBenefits.get(slot.getKind());
    }

    /**
     * 사진이 없는 슬롯에만 지도 링크를 준다(#236).
     *
     * <p>사진이 있으면 카드가 이미 설 수 있어 링크가 군더더기다. 없을 때만 "여기서 보세요" 가 값어치를 갖는다.
     *
     * <p>숙소가 이 경우의 대부분이다 — 89곳 중 45곳에서 사진 있는 숙소가 2곳도 안 된다.
     */
    private static String mapSearchUrlFor(Slot slot) {
        if (slot.getImageUrl() != null && !slot.getImageUrl().isBlank()) {
            return null;
        }
        return MapSearchLink.of(slot.getTitle(), slot.getAddress()).orElse(null);
    }
}
