package com.offway.core.leave.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 공휴일을 물을 수 있는 연도 범위(#317).
 *
 * <p>범위를 두는 이유가 성능이 아니라 <b>외부 한도</b>다 — 적재 창 밖의 해를 물으면 요청 하나가 특일정보
 * 호출 열두 번이 된다. 그래서 경계를 단위로 망라한다.
 */
class HolidayYearTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 24);

    @ParameterizedTest
    @ValueSource(ints = {2025, 2026, 2027})
    void 지난해부터_내년까지는_받는다(int year) {
        assertEquals(year, HolidayYear.of(year, TODAY).value());
    }

    @ParameterizedTest
    @ValueSource(ints = {2024, 2028, 1900, 9999})
    void 그_밖의_해는_400_으로_거절한다(int year) {
        // 빈 목록으로 답하지 않는다 — 앱이 "공휴일이 없는 해" 로 읽어 연차를 과다 계산한다.
        LeaveException thrown = assertThrows(LeaveException.class, () -> HolidayYear.of(year, TODAY));

        assertEquals(LeaveErrorCode.HOLIDAY_YEAR_OUT_OF_RANGE, thrown.errorCode());
    }

    @Test
    void 해가_바뀌면_허용_범위도_함께_움직인다() {
        // 기준일을 인자로 받는 이유다. 서버 시계를 직접 읽으면 연말 경계를 테스트로 고정할 수 없다.
        LocalDate nextYear = LocalDate.of(2027, 1, 1);

        assertEquals(2028, HolidayYear.of(2028, nextYear).value());
        assertThrows(LeaveException.class, () -> HolidayYear.of(2024, nextYear));
    }

    @Test
    void 한_해의_구간은_1월_1일부터_12월_31일까지다() {
        HolidayYear year = HolidayYear.of(2026, TODAY);

        assertEquals(LocalDate.of(2026, 1, 1), year.start());
        assertEquals(LocalDate.of(2026, 12, 31), year.end());
    }

    @Test
    void 윤년의_마지막_날도_12월_31일이다() {
        // 2월이 아니라 12월을 보므로 윤년과 무관하다. 직접 계산하지 않고 달력에 맡긴 결과를 못박는다.
        assertEquals(LocalDate.of(2028, 12, 31), HolidayYear.of(2028, LocalDate.of(2027, 6, 1)).end());
    }
}
