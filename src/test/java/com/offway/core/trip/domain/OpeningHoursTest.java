package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 오늘 여는지 판정(#189) — <b>모르면 모른다고 하는가</b>.
 *
 * <p>잘못된 단정이 침묵보다 나쁘다. "오늘 휴무" 가 틀리면 갈 수 있는 곳을 안 가고, "영업 중" 이 틀리면
 * 헛걸음한다. 실측(공주·정선 관광지 30건)에서 확실히 판정 가능한 것은 70% 였고, 나머지는 UNKNOWN 이어야 한다.
 */
class OpeningHoursTest {

    /** 2026-09-14 는 월요일. 요일 판정을 다루므로 고정 날짜를 쓴다. */
    private static final LocalDateTime MONDAY_1400 = LocalDateTime.of(2026, 9, 14, 14, 0);
    private static final LocalDateTime TUESDAY_1400 = LocalDateTime.of(2026, 9, 15, 14, 0);

    private static OpeningStatus status(String useTime, String restDate, LocalDateTime now, boolean holiday) {
        return new OpeningHours(useTime, restDate).statusAt(now, holiday);
    }

    @ParameterizedTest
    @ValueSource(strings = {"상시 개방", "상시개방", "24시간"})
    void 상시개방과_연중무휴면_항상_열림이다(String always) {
        assertEquals(OpeningStatus.OPEN, status(always, "연중무휴", MONDAY_1400, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"연중무휴", "연중 무휴"})
    void 연중무휴는_공백_변형도_받는다(String noClosing) {
        assertEquals(OpeningStatus.OPEN, status("09:00~18:00", noClosing, MONDAY_1400, false));
    }

    @ParameterizedTest
    @CsvSource({
            "09:00~18:00, 14:00, OPEN",
            "09:00~18:00, 18:00, CLOSED_NOW",   // 마감 시각은 이미 끝난 것으로 본다
            "05:30~20:00, 19:59, OPEN",
            "09:00-18:00, 14:00, OPEN",         // 하이픈 변형
            "매일 09:00~18:00, 14:00, OPEN",     // 뜻을 안 바꾸는 수식어
    })
    void 단일_시간범위는_시각을_비교한다(String useTime, String at, OpeningStatus expected) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 15, Integer.parseInt(at.split(":")[0]),
                Integer.parseInt(at.split(":")[1]));

        assertEquals(expected, status(useTime, "연중무휴", now, false));
    }

    @ParameterizedTest
    @CsvSource({
            "08:59, BEFORE_OPEN",
            "09:00, OPEN",
            "18:00, CLOSED_NOW",
    })
    void 개점_전은_운영_종료와_구분한다(String at, OpeningStatus expected) {
        // 09시에 여는 곳을 08:59 에 보고 "오늘 운영이 끝났어요" 라고 하면 갈 수 있는 곳을 안 가게 만든다.
        LocalDateTime now = LocalDateTime.of(2026, 9, 15, Integer.parseInt(at.split(":")[0]),
                Integer.parseInt(at.split(":")[1]));

        assertEquals(expected, status("09:00~18:00", "연중무휴", now, false));
    }

    @Test
    void 쉬는_요일이면_오늘_휴무다() {
        assertEquals(OpeningStatus.CLOSED_TODAY, status("09:00~18:00", "매주 월요일", MONDAY_1400, false));
    }

    @Test
    void 쉬는_요일이_아니면_시각으로_판정한다() {
        assertEquals(OpeningStatus.OPEN, status("09:00~18:00", "매주 월요일", TUESDAY_1400, false));
    }

    @Test
    void 공휴일_예외가_있으면_쉬는_요일이어도_연다() {
        // 요일만 보고 판정하면 공휴일 월요일에 "오늘 휴무" 라고 잘못 말한다 — 갈 수 있는 곳을 안 가게 만든다.
        String restDate = "매주 월요일 (단, 공휴일 및 대체공휴일은 정상운영)";

        assertEquals(OpeningStatus.OPEN, status("09:00~18:00", restDate, MONDAY_1400, true));
    }

    @Test
    void 공휴일_예외가_있어도_평일_월요일은_휴무다() {
        String restDate = "매주 월요일 (단, 공휴일 및 대체공휴일은 정상운영)";

        assertEquals(OpeningStatus.CLOSED_TODAY, status("09:00~18:00", restDate, MONDAY_1400, false));
    }

    @Test
    void 예외_조항이_없으면_공휴일이어도_휴무다() {
        assertEquals(OpeningStatus.CLOSED_TODAY, status("09:00~18:00", "매주 월요일", MONDAY_1400, true));
    }

    @Test
    void 휴무일이_먼저다() {
        // 문을 아예 안 여는 날에 "운영이 끝났어요" 라고 하면 내일은 갈 수 있다는 뜻으로 읽힌다.
        LocalDateTime mondayNight = LocalDateTime.of(2026, 9, 14, 23, 0);

        assertEquals(OpeningStatus.CLOSED_TODAY, status("09:00~18:00", "매주 월요일", mondayNight, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[하절기] 09:00~18:00 / [동절기] 10:00~17:00",   // 계절별
            "※ 자세한 사항은 전화문의 요망",                    // 정보 없음
            "[미사시간] - 월요일 09:30 / 화요일 10:00",         // 다른 의미
            "09:00~18:00 (동절기 17:00까지)",                 // 단일 범위가 아니다
    })
    void 확실하지_않은_시간_형식은_모른다고_한다(String useTime) {
        // 30% 를 위해 70% 의 신뢰를 깎지 않는다.
        assertEquals(OpeningStatus.UNKNOWN, status(useTime, "연중무휴", MONDAY_1400, false));
    }

    @Test
    void 시설이_여럿이면_판정하지_않는다() {
        String useTime = "[우금치전적] 상시개방 / [알림터] 09:00~18:00";

        assertEquals(OpeningStatus.UNKNOWN, status(useTime, "연중무휴", MONDAY_1400, false));
        assertEquals(OpeningStatus.UNKNOWN,
                status("상시 개방", "[우금치전적] 연중무휴 / [알림터] 매주 월요일", MONDAY_1400, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1월 1일 / 설·추석 당일", "명절 당일"})
    void 특정일_휴무는_다루지_않는다(String restDate) {
        assertEquals(OpeningStatus.UNKNOWN, status("09:00~18:00", restDate, MONDAY_1400, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "연중무휴 (단, 설·추석 당일 휴무)",
            "연중무휴(1월 1일, 설·추석 당일 휴무)",
            "매주 월요일, 1월 1일, 설·추석 당일",          // 요일은 읽히지만 특정일이 남는다
            "매주 월요일(공휴일인 경우 다음 날 휴무)",       // 우리가 모르는 예외 조항
    })
    void 못_읽은_조건이_남으면_확정하지_않는다(String restDate) {
        // `연중무휴` 만 보고 OPEN 을 확정하면 설 당일에 "영업 중" 이라고 말한다 — 헛걸음을 만든다.
        assertEquals(OpeningStatus.UNKNOWN, status("09:00~18:00", restDate, TUESDAY_1400, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "상시개방 (동절기 제외)",
            "24시간 (동절기 제외)",
            "하절기 09:00~18:00",                       // 계절 한정인데 단일 범위로 읽힌다
            "동절기 09:00~17:00",
            "09:00~12:00, 13:00~18:00",                 // 범위가 둘
    })
    void 운영시간에_못_읽은_조건이_남으면_확정하지_않는다(String useTime) {
        assertEquals(OpeningStatus.UNKNOWN, status(useTime, "연중무휴", TUESDAY_1400, false));
    }

    @Test
    void 연중무휴와_정기휴무가_함께_오면_모른다고_한다() {
        // 서로 어긋난다 — 어느 쪽이 맞는지 우리가 모른다.
        assertEquals(OpeningStatus.UNKNOWN, status("09:00~18:00", "연중무휴, 매주 월요일", MONDAY_1400, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"매주 월요일", "매주 월요일 휴관", "매주 월요일 정기휴무"})
    void 뜻을_안_바꾸는_말이_붙어도_읽는다(String restDate) {
        // 남기는 쪽을 나열하면 여기 빠진 표현이 UNKNOWN 이 될 뿐이라, 실측으로 볼 때마다 늘리면 된다.
        assertEquals(OpeningStatus.CLOSED_TODAY, status("09:00~18:00", restDate, MONDAY_1400, false));
    }

    @Test
    void 상시개방은_붙은_말을_읽어도_열림이다() {
        assertEquals(OpeningStatus.OPEN, status("24시간 개방", "연중무휴", MONDAY_1400, false));
    }

    @Test
    void 자정을_넘기는_영업은_판정하지_않는다() {
        // 22:00~02:00 을 그대로 비교하면 낮 시간이 전부 "운영 끝" 으로 나온다.
        assertEquals(OpeningStatus.UNKNOWN, status("22:00~02:00", "연중무휴", MONDAY_1400, false));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 값이_없으면_모른다고_한다(String blank) {
        assertEquals(OpeningStatus.UNKNOWN, status(blank, blank, MONDAY_1400, false));
        assertEquals(OpeningStatus.UNKNOWN, status("09:00~18:00", blank, MONDAY_1400, false));
        assertEquals(OpeningStatus.UNKNOWN, status(blank, "연중무휴", MONDAY_1400, false));
    }

    @Test
    void 판정_시각이_없으면_모른다고_한다() {
        assertEquals(OpeningStatus.UNKNOWN, new OpeningHours("상시 개방", "연중무휴").statusAt(null, false));
    }

    @Test
    void 모르는_상태는_화면에_안_내린다() {
        assertEquals(false, OpeningStatus.UNKNOWN.isDisplayable());
        assertEquals(true, OpeningStatus.CLOSED_TODAY.isDisplayable());
        assertEquals(true, OpeningStatus.BEFORE_OPEN.isDisplayable());
    }
}
