package com.offway.core.trip.infrastructure.tour.dto;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * 축제 하나의 기간(#388) — {@code searchFestival2} 응답 한 줄.
 *
 * <p><b>{@code areaBasedList2} 는 이 날짜를 주지 않는다.</b> 그래서 축제가 볼거리 풀에 들어와 있으면서도
 * 언제 열리는지 모르는 상태였다. 기간을 아는 오퍼레이션은 이것 하나뿐이다.
 *
 * @param contentId {@code region_poi} 와 같은 키. 이걸로 기존 장소에 기간을 붙인다
 * @param title 조회 당시 축제명. 사람이 로그를 읽을 때 쓴다
 * @param eventStart 행사 시작일
 * @param eventEnd 행사 종료일
 */
public record TourFestival(String contentId, String title, LocalDate eventStart, LocalDate eventEnd) {

    /** TourAPI 가 주는 날짜 형식 — {@code 20260912} 처럼 구분자가 없다. */
    private static final DateTimeFormatter API_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 응답 한 줄을 기간으로 옮긴다 — <b>날짜가 온전할 때만</b>.
     *
     * <h2>왜 셋 중 하나라도 없으면 버리나</h2>
     *
     * <p>이 값의 쓸모는 "지금 열리나" 를 판정하는 것 하나다. 시작·종료 중 하나만 있으면 그 판정을 못 하고,
     * 억지로 채우면 <b>모르는 것을 안다고 말하는</b> 셈이 된다.
     *
     * <p>버린 행은 저장되지 않으므로 그 축제는 <b>지금처럼 평범한 볼거리로 남는다</b> — 없는 것과 "모른다"
     * 를 구분하려는 것이고, 모르는 것을 끝났다고 단정하지 않기 위해서다({@code checked_on} 과 같은 판단).
     *
     * <p>거꾸로 온 날짜(시작 &gt; 종료)도 버린다. 그대로 두면 어떤 날짜에도 열리지 않는 축제가 되어,
     * 있는 축제를 우리가 지우는 셈이 된다.
     */
    public static Optional<TourFestival> of(String contentId, String title, String startText, String endText) {
        if (isBlank(contentId)) {
            return Optional.empty();
        }
        LocalDate start = parse(startText);
        LocalDate end = parse(endText);
        if (start == null || end == null || start.isAfter(end)) {
            return Optional.empty();
        }
        return Optional.of(new TourFestival(contentId.strip(), blankToNull(title), start, end));
    }

    /** 여행일에 이 축제가 열려 있는가 — 시작일·종료일 당일을 포함한다. */
    public boolean isOpenOn(LocalDate date) {
        return !date.isBefore(eventStart) && !date.isAfter(eventEnd);
    }

    private static LocalDate parse(String text) {
        if (isBlank(text)) {
            return null;
        }
        try {
            return LocalDate.parse(text.strip(), API_DATE);
        } catch (DateTimeParseException e) {
            // 형식이 어긋난 값은 없는 것으로 본다. 여기서 던지면 한 줄 때문에 한 페이지가 통째로 날아간다.
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.strip();
    }
}
