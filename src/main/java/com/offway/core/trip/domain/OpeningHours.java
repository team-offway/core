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
            Pattern.compile("(\\d{1,2}):(\\d{2})\\s*[~\\-–—]\\s*(\\d{1,2}):(\\d{2})");

    /** {@code 연중무휴}·{@code 연중 무휴} — 휴무 없음. */
    private static final Pattern NO_CLOSING = Pattern.compile("연\\s*중\\s*무\\s*휴");

    /** {@code 매주 월요일} — 정기 휴무 요일. */
    private static final Pattern WEEKLY_CLOSED = Pattern.compile("매\\s*주\\s*([월화수목금토일])\\s*요일");

    /**
     * {@code (단, 공휴일 및 대체공휴일은 정상운영)} — <b>예외 조항</b>.
     *
     * <p>이걸 무시하면 공휴일 월요일에 "오늘 휴무" 라고 잘못 말한다. 사용자가 갈 수 있는 곳을 안 가게 만든다.
     *
     * <p>{@code 단,} 을 함께 먹는다 — 남겨 두면 {@link #REST_OF_IT} 가 "못 읽은 조건" 으로 세어 예외 조항을
     * 알아본 보람이 사라진다.
     */
    private static final Pattern HOLIDAY_EXCEPTION =
            Pattern.compile("(?:단\\s*[,、]?\\s*)?공휴일.{0,20}정상\\s*운영");

    /** 여러 시설이 한 필드에 들어온 형식({@code [우금치전적] 상시개방 / [알림터] 09:00~}) — 판정하지 않는다. */
    private static final Pattern MULTI_FACILITY = Pattern.compile("\\[.+\\].*/.*\\[.+\\]");

    /**
     * <b>읽어도 뜻이 안 바뀌는 것</b> — 공백·구두점과, 판정을 흔들지 않는 수식어·조사뿐이다.
     *
     * <p>알아본 조각을 지운 뒤 이것까지 지워서 <b>아무것도 안 남아야</b> 확정한다({@link #understood}).
     * 남은 글자는 곧 <b>우리가 못 읽은 조건</b>이다 — {@code 동절기 제외}·{@code 설·추석 당일 휴무}·
     * {@code 공휴일인 경우 다음 날 휴무} 가 그렇게 걸린다.
     *
     * <p><b>지우는 쪽이 아니라 남기는 쪽을 나열한 이유.</b> 위험한 문구를 골라 막으면(blocklist) 처음 보는
     * 표현이 그대로 통과해 <b>틀린 단정</b>이 된다. 반대로 여기 빠진 표현은 {@code UNKNOWN} 이 될 뿐이라
     * 화면이 침묵한다 — 실측으로 형식을 더 볼 때마다 이 목록을 늘리면 된다.
     */
    private static final Pattern REST_OF_IT = Pattern.compile(
            "[\\s\\p{Punct}~–—·ㆍ、]"
                    + "|매일|연중|상시|개방|운영|영업|관람|이용|입장|시간"
                    + "|부터|까지|단|정기|휴무|휴관|휴장|휴원|휴점");

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
     *
     * <p><b>알아본 조각을 지우고 아무것도 안 남을 때만 확정한다.</b> {@code 연중무휴} 만 찾아 확정하면
     * {@code 연중무휴 (단, 설·추석 당일 휴무)} 가 설 당일에 "영업 중" 이 된다 — 헛걸음이다.
     */
    private OpeningStatus closedToday(LocalDate today, boolean isHoliday) {
        if (isBlank(restDate) || MULTI_FACILITY.matcher(restDate).find()) {
            return OpeningStatus.UNKNOWN;
        }
        Set<DayOfWeek> closedDays = weeklyClosedDays();
        boolean noClosing = NO_CLOSING.matcher(restDate).find();
        // `1월 1일 / 설·추석 당일` 같은 특정일도, 우리가 모르는 예외 조항도 전부 여기서 걸린다.
        String rest = erase(erase(erase(restDate, NO_CLOSING), WEEKLY_CLOSED), HOLIDAY_EXCEPTION);
        if (!understood(rest)) {
            return OpeningStatus.UNKNOWN;
        }
        if (noClosing) {
            // `연중무휴` 와 `매주 월요일` 이 함께 오면 서로 어긋난다 — 둘 중 어느 쪽인지 우리가 모른다.
            return closedDays.isEmpty() ? OpeningStatus.OPEN : OpeningStatus.UNKNOWN;
        }
        if (closedDays.isEmpty()) {
            return OpeningStatus.UNKNOWN; // 알아본 것이 하나도 없다 — 지워서 빈 것과 읽어서 빈 것은 다르다
        }
        if (!closedDays.contains(today.getDayOfWeek())) {
            return OpeningStatus.OPEN;
        }
        // 공휴일 예외 — 쉬는 요일이어도 공휴일이면 연다.
        boolean opensOnHoliday = isHoliday && HOLIDAY_EXCEPTION.matcher(restDate).find();
        return opensOnHoliday ? OpeningStatus.OPEN : OpeningStatus.CLOSED_TODAY;
    }

    /** {@code 매주 월요일·목요일} 처럼 여럿일 수 있다. */
    private Set<DayOfWeek> weeklyClosedDays() {
        Set<DayOfWeek> closedDays = EnumSet.noneOf(DayOfWeek.class);
        Matcher weekly = WEEKLY_CLOSED.matcher(restDate);
        while (weekly.find()) {
            closedDays.add(dayOf(weekly.group(1)));
        }
        return closedDays;
    }

    /**
     * 시각 판정 — 상시개방이면 항상 열림, 단일 범위면 비교, 그 밖은 모른다.
     *
     * <p>여기서도 <b>알아본 조각을 지운 나머지가 비어야</b> 확정한다. {@code 하절기 09:00~18:00} 은 범위가
     * 온전히 읽히지만 그 시간은 <b>여름에만</b> 유효하다 — 겨울에 그대로 쓰면 틀린다.
     */
    private OpeningStatus openNow(LocalTime now) {
        if (isBlank(useTime) || MULTI_FACILITY.matcher(useTime).find()) {
            return OpeningStatus.UNKNOWN;
        }
        if (ALWAYS_OPEN.matcher(useTime).find()) {
            return understood(erase(useTime, ALWAYS_OPEN)) ? OpeningStatus.OPEN : OpeningStatus.UNKNOWN;
        }
        Matcher range = SINGLE_RANGE.matcher(useTime);
        if (!range.find()) {
            return OpeningStatus.UNKNOWN;
        }
        LocalTime open = time(range.group(1), range.group(2));
        LocalTime close = time(range.group(3), range.group(4));
        if (range.find()) {
            return OpeningStatus.UNKNOWN; // 계절별·오전오후 분리처럼 범위가 둘 이상이면 어느 쪽인지 모른다
        }
        if (!understood(erase(useTime, SINGLE_RANGE))) {
            return OpeningStatus.UNKNOWN;
        }
        if (open == null || close == null) {
            return OpeningStatus.UNKNOWN;
        }
        // 자정을 넘기는 영업(22:00~02:00)은 다루지 않는다 — 흔치 않고, 틀리면 헛걸음을 만든다.
        if (!close.isAfter(open)) {
            return OpeningStatus.UNKNOWN;
        }
        if (now.isBefore(open)) {
            return OpeningStatus.BEFORE_OPEN;
        }
        return now.isBefore(close) ? OpeningStatus.OPEN : OpeningStatus.CLOSED_NOW;
    }

    /** 알아본 조각을 지운다 — 자리는 공백으로 남겨 앞뒤 글자가 붙어 새 단어가 되지 않게 한다. */
    private static String erase(String text, Pattern understood) {
        return understood.matcher(text).replaceAll(" ");
    }

    /** 남은 글자가 {@link #REST_OF_IT} 뿐인가 — 아니면 우리가 못 읽은 조건이 있다는 뜻이다. */
    private static boolean understood(String rest) {
        return REST_OF_IT.matcher(rest).replaceAll("").isEmpty();
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
