package com.offway.core.trip.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 장소의 운영시간·휴무일(#157) — 코스 슬롯에 인라인으로 나가는 두 값과, 그것으로 <b>오늘 여는지</b>를
 * 판정하는 규칙(#189).
 *
 * <p>{@link PoiIntro} 는 카테고리별 보조정보 전부를 담지만, <b>슬롯이 필요한 건 이 둘뿐</b>이다.
 *
 * <p>둘 다 <b>자유 텍스트</b>다. 관광 API 가 {@code 상시 개방}·{@code 09:00~18:00}·
 * {@code [하절기] 09:00~18:00 / [동절기] 10:00~17:00} 처럼 제각각 준다. 해석은 여기가 소유한다 —
 * 클라이언트마다 파싱하면 클라이언트마다 다르게 틀린다.
 */
public record OpeningHours(String useTime, String restDate) {

    /** {@code 상시 개방}·{@code 상시개방}·{@code 24시간} — 공백 변형을 함께 받는다. */
    private static final Pattern ALWAYS_OPEN = Pattern.compile("상\\s*시\\s*개\\s*방|24\\s*시간");

    /** {@code 09:00~18:00} 단일 범위. 물결·하이픈·틸드를 모두 받는다. */
    private static final Pattern SINGLE_RANGE =
            Pattern.compile("^\\D{0,4}(\\d{1,2}):(\\d{2})\\s*[~\\-–—]\\s*(\\d{1,2}):(\\d{2})\\D{0,4}$");

    /** {@code 연중무휴}·{@code 연중 무휴} — 휴무 없음. */
    private static final Pattern NO_CLOSING = Pattern.compile("연\\s*중\\s*무\\s*휴");

    /** {@code 매주 월요일} — 정기 휴무 요일. */
    private static final Pattern WEEKLY_CLOSED = Pattern.compile("매\\s*주\\s*([월화수목금토일])\\s*요일");

    /**
     * {@code (단, 공휴일 및 대체공휴일은 정상운영)} — <b>예외 조항</b>.
     *
     * <p>이걸 무시하면 공휴일 월요일에 "오늘 휴무" 라고 잘못 말한다. 사용자가 갈 수 있는 곳을 안 가게 만든다.
     */
    private static final Pattern HOLIDAY_EXCEPTION = Pattern.compile("공휴일.{0,20}정상\\s*운영");

    /** 여러 시설이 한 필드에 들어온 형식({@code [우금치전적] 상시개방 / [알림터] 09:00~}) — 판정하지 않는다. */
    private static final Pattern MULTI_FACILITY = Pattern.compile("\\[.+\\].*/.*\\[.+\\]");

    private static final Set<DayOfWeek> DAYS = EnumSet.allOf(DayOfWeek.class);

    /** 둘 다 없으면 실을 이유가 없다 — 빈 값을 내리면 화면이 빈 줄을 그린다. */
    public boolean isEmpty() {
        return isBlank(useTime) && isBlank(restDate);
    }

    /**
     * 오늘 이 장소가 열려 있는가(#189).
     *
     * <p>순서가 중요하다 — <b>휴무일을 먼저</b> 본다. 문을 아예 안 여는 날에 "운영이 끝났어요" 라고 하면
     * 내일은 갈 수 있다는 뜻으로 읽힌다.
     *
     * @param now 판정 기준 시각(KST). 호출자가 여행일이 오늘인지 먼저 확인한다
     * @param isHoliday 오늘이 공휴일인가 — 예외 조항({@code 공휴일은 정상운영}) 판정에 쓴다
     */
    public OpeningStatus statusAt(java.time.LocalDateTime now, boolean isHoliday) {
        if (now == null) {
            return OpeningStatus.UNKNOWN;
        }
        OpeningStatus closing = closedToday(now.toLocalDate(), isHoliday);
        if (closing != OpeningStatus.UNKNOWN) {
            return closing == OpeningStatus.CLOSED_TODAY ? OpeningStatus.CLOSED_TODAY : openNow(now.toLocalTime());
        }
        return OpeningStatus.UNKNOWN;
    }

    /**
     * 휴무 판정 — 오늘 쉬면 {@link OpeningStatus#CLOSED_TODAY}, 안 쉬는 게 확실하면 {@link OpeningStatus#OPEN},
     * 모르면 {@link OpeningStatus#UNKNOWN}.
     */
    private OpeningStatus closedToday(LocalDate today, boolean isHoliday) {
        if (isBlank(restDate) || MULTI_FACILITY.matcher(restDate).find()) {
            return OpeningStatus.UNKNOWN;
        }
        if (NO_CLOSING.matcher(restDate).find()) {
            return OpeningStatus.OPEN;
        }
        Matcher weekly = WEEKLY_CLOSED.matcher(restDate);
        if (!weekly.find()) {
            return OpeningStatus.UNKNOWN; // `1월 1일 / 설·추석 당일` 같은 특정일은 다루지 않는다
        }
        Set<DayOfWeek> closedDays = EnumSet.noneOf(DayOfWeek.class);
        do {
            closedDays.add(dayOf(weekly.group(1)));
        } while (weekly.find());

        if (!closedDays.contains(today.getDayOfWeek())) {
            return OpeningStatus.OPEN;
        }
        // 공휴일 예외 — 쉬는 요일이어도 공휴일이면 연다.
        boolean opensOnHoliday = isHoliday && HOLIDAY_EXCEPTION.matcher(restDate).find();
        return opensOnHoliday ? OpeningStatus.OPEN : OpeningStatus.CLOSED_TODAY;
    }

    /** 시각 판정 — 상시개방이면 항상 열림, 단일 범위면 비교, 그 밖은 모른다. */
    private OpeningStatus openNow(LocalTime now) {
        if (isBlank(useTime) || MULTI_FACILITY.matcher(useTime).find()) {
            return OpeningStatus.UNKNOWN;
        }
        if (ALWAYS_OPEN.matcher(useTime).find()) {
            return OpeningStatus.OPEN;
        }
        Matcher range = SINGLE_RANGE.matcher(useTime.trim());
        if (!range.matches()) {
            return OpeningStatus.UNKNOWN; // 계절별·복수 범위는 억지로 파싱하지 않는다
        }
        LocalTime open = time(range.group(1), range.group(2));
        LocalTime close = time(range.group(3), range.group(4));
        if (open == null || close == null) {
            return OpeningStatus.UNKNOWN;
        }
        // 자정을 넘기는 영업(22:00~02:00)은 다루지 않는다 — 흔치 않고, 틀리면 헛걸음을 만든다.
        if (!close.isAfter(open)) {
            return OpeningStatus.UNKNOWN;
        }
        return now.isBefore(open) || !now.isBefore(close) ? OpeningStatus.CLOSED_NOW : OpeningStatus.OPEN;
    }

    private static LocalTime time(String hour, String minute) {
        int h = Integer.parseInt(hour);
        int m = Integer.parseInt(minute);
        return h > 23 || m > 59 ? null : LocalTime.of(h, m);
    }

    private static DayOfWeek dayOf(String korean) {
        return switch (korean) {
            case "월" -> DayOfWeek.MONDAY;
            case "화" -> DayOfWeek.TUESDAY;
            case "수" -> DayOfWeek.WEDNESDAY;
            case "목" -> DayOfWeek.THURSDAY;
            case "금" -> DayOfWeek.FRIDAY;
            case "토" -> DayOfWeek.SATURDAY;
            default -> DayOfWeek.SUNDAY;
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
