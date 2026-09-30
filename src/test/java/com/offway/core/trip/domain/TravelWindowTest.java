package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 여행 구간 계산(#616).
 *
 * <p>"시작일 + 며칠" 을 날짜 둘로 푸는 자리다. 여기가 하루씩 어긋나면 후보를 거르는 구간과 날에
 * 배치하는 구간이 갈라져, <b>후보엔 있는데 아무 날에도 못 들어가는 축제</b>가 생긴다.
 */
class TravelWindowTest {

    private static final LocalDate 오월일일 = LocalDate.of(2026, 5, 1);

    @DisplayName("하루 여행은 시작일과 종료일이 같다")
    @Test
    void 하루_여행은_끝일이_시작일과_같다() {
        TravelWindow window = TravelWindow.of(오월일일, 1);

        assertEquals(오월일일, window.start());
        assertEquals(오월일일, window.end());
        assertEquals(1, window.days());
    }

    /**
     * {@code -1} 을 빠뜨리면 구간이 하루 길어진다.
     *
     * <p>그러면 마지막 날 <b>다음날</b>에 시작하는 축제까지 후보로 올라오고, 그 축제는 어느 날에도
     * 못 들어간다.
     */
    @DisplayName("n일 여행은 시작일부터 n일째까지다")
    @ParameterizedTest
    @CsvSource({"1, 2026-05-01", "2, 2026-05-02", "3, 2026-05-03", "7, 2026-05-07"})
    void 일수만큼_끝일이_정해진다(int travelDays, String 끝일) {
        TravelWindow window = TravelWindow.of(오월일일, travelDays);

        assertEquals(LocalDate.parse(끝일), window.end());
        assertEquals(travelDays, window.days());
    }

    @DisplayName("0일·음수 일수는 하루로 본다")
    @ParameterizedTest
    @CsvSource({"0", "-1", "-99"})
    void 일수가_1보다_작으면_하루다(int travelDays) {
        assertEquals(1, TravelWindow.of(오월일일, travelDays).days());
    }

    @DisplayName("시작일·종료일 당일을 포함한다")
    @ParameterizedTest
    @CsvSource({
        "2026-04-30, false",
        "2026-05-01, true",
        "2026-05-02, true",
        "2026-05-03, true",
        "2026-05-04, false",
    })
    void 구간_안인지_당일을_포함해_본다(String 날짜, boolean 안에있나) {
        TravelWindow window = TravelWindow.of(오월일일, 3);

        assertEquals(안에있나, window.covers(LocalDate.parse(날짜)));
    }

    /**
     * 겹침 판정 — <b>여행 내내 열려야 하는 것이 아니다</b>.
     *
     * <p>2박3일 중 마지막 날 하루만 하는 축제도 갈 수 있는 축제다. 이 판정이 "완전히 포함" 으로
     * 좁아지면 대부분의 축제가 사라진다.
     */
    @DisplayName("하루라도 겹치면 겹친 것이다")
    @ParameterizedTest
    @CsvSource({
        // 여행 2026-05-01 ~ 05-03
        "2026-04-01, 2026-04-30, false, 여행 전에 끝남",
        "2026-04-28, 2026-05-01, true,  첫날에 종료",
        "2026-05-02, 2026-05-02, true,  가운데 하루만",
        "2026-05-03, 2026-05-10, true,  마지막날에 시작",
        "2026-05-04, 2026-05-10, false, 여행 뒤에 시작",
        "2026-01-01, 2026-12-31, true,  여행을 통째로 덮음",
    })
    void 하루라도_겹치면_참이다(String 시작, String 종료, boolean 겹치나, String 상황) {
        TravelWindow window = TravelWindow.of(오월일일, 3);

        assertEquals(겹치나, window.overlaps(LocalDate.parse(시작), LocalDate.parse(종료)), 상황);
    }

    /** 기간을 모르면 겹치는지 답할 수 없다 — 거짓으로 두고, 부르는 쪽이 "모른다" 를 따로 다룬다. */
    @DisplayName("기간을 모르면 겹친다고 하지 않는다")
    @Test
    void 기간이_없으면_겹치지_않는다() {
        TravelWindow window = TravelWindow.of(오월일일, 3);

        assertFalse(window.overlaps(null, 오월일일));
        assertFalse(window.overlaps(오월일일, null));
        assertFalse(window.overlaps(null, null));
    }

    @DisplayName("n일차의 날짜를 답한다")
    @ParameterizedTest
    @CsvSource({"1, 2026-05-01", "2, 2026-05-02", "3, 2026-05-03"})
    void 몇일차의_날짜를_안다(int dayNumber, String 날짜) {
        assertEquals(LocalDate.parse(날짜), TravelWindow.of(오월일일, 3).dateOf(dayNumber));
    }

    /** 구간 밖을 물으면 불변식 위반이다 — 호출자가 여행 일수를 잘못 센 것이라 버그 신호로 터뜨린다. */
    @DisplayName("구간 밖의 날을 물으면 터진다")
    @ParameterizedTest
    @CsvSource({"0", "4", "-1"})
    void 구간_밖의_날은_불변식_위반이다(int dayNumber) {
        TravelWindow window = TravelWindow.of(오월일일, 3);

        assertThrows(IllegalStateException.class, () -> window.dateOf(dayNumber));
    }

    @DisplayName("1일차부터 차례로 날짜를 낸다")
    @Test
    void 날짜를_차례로_낸다() {
        assertEquals(
                java.util.List.of(
                        LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 3)),
                TravelWindow.of(오월일일, 3).dates().toList());
    }

    @DisplayName("종료일이 시작일보다 앞서면 만들 수 없다")
    @Test
    void 거꾸로된_구간은_못_만든다() {
        assertThrows(IllegalStateException.class,
                () -> new TravelWindow(오월일일, 오월일일.minusDays(1)));
    }

    @DisplayName("시작일 없이 만들 수 없다")
    @Test
    void 시작일은_필수다() {
        assertThrows(NullPointerException.class, () -> TravelWindow.of(null, 3));
    }

    /** 첫날에만 열리는 축제가 3일 여행에 후보로 올라오는지 — #616 이 고친 그 상황. */
    @DisplayName("첫날만 하는 축제도 3일 여행 후보다")
    @Test
    void 첫날만_하는_축제도_겹친다() {
        TravelWindow window = TravelWindow.of(오월일일, 3);

        assertTrue(window.overlaps(오월일일, 오월일일), "첫날만 하는 축제가 후보에서 빠지고 있다");
    }

    /** 반대쪽 — 셋째 날에만 열리는 축제. 예전에는 첫날로만 걸러 이것이 통째로 사라졌다. */
    @DisplayName("셋째 날만 하는 축제도 3일 여행 후보다")
    @Test
    void 마지막날만_하는_축제도_겹친다() {
        TravelWindow window = TravelWindow.of(오월일일, 3);
        LocalDate 셋째날 = 오월일일.plusDays(2);

        assertTrue(window.overlaps(셋째날, 셋째날), "셋째 날 축제가 후보에서 빠지고 있다");
    }
}
