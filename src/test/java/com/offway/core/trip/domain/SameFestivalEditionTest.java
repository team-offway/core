package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 같은 축제 회차 판정.
 *
 * <p>이 판정이 <b>느슨하면</b> 서로 다른 연도의 회차가 하나로 접혀 없는 기간이 만들어진다. <b>빡빡하면</b>
 * 지자체가 날짜를 고쳐 다시 올린 행이 둘 다 남아 어느 쪽이 뽑히느냐가 날짜를 정한다.
 */
class SameFestivalEditionTest {

    private static final String 이름 = "안동국제탈춤페스티벌";

    private static boolean 같은회차(String 시작A, String 종료A, String 시작B, String 종료B) {
        return SameFestivalEdition.is(
                이름, LocalDate.parse(시작A), LocalDate.parse(종료A),
                이름, LocalDate.parse(시작B), LocalDate.parse(종료B));
    }

    /**
     * 실측된 그 한 건(2026-09-29 운영).
     *
     * <p>같은 지역·같은 이름(바이트 단위 동일)·같은 종료일인데 시작일만 하루 다르다. 지자체가 시작일을
     * 고쳐 다시 올린 것으로 보이는데, 자연키가 (지역·이름·시작일)이라 두 행이 남았다.
     */
    @DisplayName("시작일만 하루 다른 두 행은 같은 회차다")
    @Test
    void 시작일만_하루_다르면_같은_회차다() {
        assertTrue(같은회차("2026-09-24", "2026-10-04", "2026-09-25", "2026-10-04"));
    }

    /**
     * <b>연도가 다른 회차는 접지 않는다.</b>
     *
     * <p>원본이 과거 회차(2023~2025)를 함께 싣는다. 이름만 보고 접으면 그것들이 한 덩이가 된다.
     */
    @DisplayName("연도가 다른 회차는 같은 회차가 아니다")
    @ParameterizedTest
    @CsvSource({
        "2023-09-22, 2023-10-01, 2026-09-25, 2026-10-04",
        "2024-10-11, 2024-10-20, 2026-09-25, 2026-10-04",
        "2025-10-17, 2025-10-26, 2026-09-25, 2026-10-04",
    })
    void 연도가_다르면_다른_회차다(String 시작A, String 종료A, String 시작B, String 종료B) {
        assertFalse(같은회차(시작A, 종료A, 시작B, 종료B), "다른 연도 회차가 접히고 있다");
    }

    @DisplayName("기간이 겹치는 여러 모양을 같은 회차로 본다")
    @ParameterizedTest
    @CsvSource({
        "2026-09-24, 2026-10-04, 2026-09-24, 2026-10-04, 완전히 같음",
        "2026-09-24, 2026-10-04, 2026-10-04, 2026-10-10, 하루만 겹침",
        "2026-09-24, 2026-10-04, 2026-09-20, 2026-09-24, 시작일에 겹침",
        "2026-09-24, 2026-10-04, 2026-09-28, 2026-09-29, 안에 들어감",
    })
    void 하루라도_겹치면_같은_회차다(String 시작A, String 종료A, String 시작B, String 종료B, String 상황) {
        assertTrue(같은회차(시작A, 종료A, 시작B, 종료B), 상황);
    }

    @DisplayName("하루도 안 겹치면 다른 회차다")
    @ParameterizedTest
    @CsvSource({
        "2026-09-24, 2026-10-04, 2026-10-05, 2026-10-10, 하루 뒤에 시작",
        "2026-09-24, 2026-10-04, 2026-09-01, 2026-09-23, 하루 앞에 종료",
    })
    void 안_겹치면_다른_회차다(String 시작A, String 종료A, String 시작B, String 종료B, String 상황) {
        assertFalse(같은회차(시작A, 종료A, 시작B, 종료B), 상황);
    }

    @DisplayName("이름이 다르면 기간이 겹쳐도 다른 축제다")
    @Test
    void 이름이_다르면_다른_축제다() {
        assertFalse(SameFestivalEdition.is(
                "안동국제탈춤페스티벌", LocalDate.of(2026, 9, 24), LocalDate.of(2026, 10, 4),
                "안동한우축제", LocalDate.of(2026, 9, 24), LocalDate.of(2026, 10, 4)));
    }

    @DisplayName("띄어쓰기·대소문자 차이는 같은 이름으로 본다")
    @ParameterizedTest
    @CsvSource({
        "안동 국제 탈춤 페스티벌",
        "안동국제탈춤페스티벌 ",
        " 안동국제탈춤페스티벌",
    })
    void 띄어쓰기가_달라도_같은_이름이다(String 다른표기) {
        assertTrue(SameFestivalEdition.is(
                이름, LocalDate.of(2026, 9, 24), LocalDate.of(2026, 10, 4),
                다른표기, LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 4)));
    }

    /**
     * 기간을 모르면 접지 않는다.
     *
     * <p>가릴 수 없는 것을 접으면 다른 회차를 하나로 만들 수 있다. 중복은 하나가 뽑히면 끝이지만,
     * 잘못 접으면 <b>없는 기간이 만들어진다</b>.
     */
    @DisplayName("기간을 모르면 같은 회차라고 하지 않는다")
    @Test
    void 기간을_모르면_접지_않는다() {
        LocalDate 어느날 = LocalDate.of(2026, 9, 24);

        assertFalse(SameFestivalEdition.is(이름, null, 어느날, 이름, 어느날, 어느날));
        assertFalse(SameFestivalEdition.is(이름, 어느날, null, 이름, 어느날, 어느날));
        assertFalse(SameFestivalEdition.is(이름, 어느날, 어느날, 이름, null, 어느날));
        assertFalse(SameFestivalEdition.is(이름, 어느날, 어느날, 이름, 어느날, null));
    }

    @DisplayName("이름이 없으면 같다고 하지 않는다")
    @Test
    void 이름이_없으면_접지_않는다() {
        LocalDate 어느날 = LocalDate.of(2026, 9, 24);

        assertFalse(SameFestivalEdition.is(null, 어느날, 어느날, 이름, 어느날, 어느날));
        assertFalse(SameFestivalEdition.is("", 어느날, 어느날, "", 어느날, 어느날));
        assertFalse(SameFestivalEdition.is("   ", 어느날, 어느날, "   ", 어느날, 어느날));
    }

    /**
     * 교집합을 쓴다 — <b>늦은 시작, 이른 종료</b>.
     *
     * <p>어느 쪽이 맞는지 모르므로, 한쪽만 주장하는 날에 사용자를 보내지 않는다. 문 닫힌 곳에 보내는
     * 것보다 하루를 덜 매칭하는 것이 낫다.
     */
    @DisplayName("함께 인정하는 기간만 남긴다")
    @Test
    void 교집합_기간을_쓴다() {
        LocalDate 이십사일 = LocalDate.of(2026, 9, 24);
        LocalDate 이십오일 = LocalDate.of(2026, 9, 25);
        LocalDate 사일 = LocalDate.of(2026, 10, 4);
        LocalDate 십일 = LocalDate.of(2026, 10, 10);

        assertEquals(이십오일, SameFestivalEdition.laterStart(이십사일, 이십오일));
        assertEquals(이십오일, SameFestivalEdition.laterStart(이십오일, 이십사일));
        assertEquals(사일, SameFestivalEdition.earlierEnd(사일, 십일));
        assertEquals(사일, SameFestivalEdition.earlierEnd(십일, 사일));
    }

    @DisplayName("같은 날짜면 그 날짜를 그대로 쓴다")
    @Test
    void 같은_날짜면_그대로다() {
        LocalDate 어느날 = LocalDate.of(2026, 9, 24);

        assertEquals(어느날, SameFestivalEdition.laterStart(어느날, 어느날));
        assertEquals(어느날, SameFestivalEdition.earlierEnd(어느날, 어느날));
    }
}
