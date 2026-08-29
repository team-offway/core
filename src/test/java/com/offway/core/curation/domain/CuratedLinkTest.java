package com.offway.core.curation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.exception.ErrorCode;
import org.junit.jupiter.api.function.Executable;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class CuratedLinkTest {

    private static final LocalDate STARTS = LocalDate.of(2026, 8, 1);
    private static final LocalDate ENDS = LocalDate.of(2026, 8, 31);

    /** 상시가 아닌 기본 링크 — 각 테스트가 자기가 검사할 값만 바꿔 만든다. */
    private static CuratedLink.CuratedLinkBuilder valid() {
        return CuratedLink.builder()
                .title("2026 대한민국 숙박세일 페스타")
                .chipText("숙박 3만원 할인")
                .linkUrl("https://ktostay.visitkorea.or.kr")
                .endsOn(ENDS)
                .surfaces(Set.of(Surface.HOME));
    }

    private static CuratedLink link(LocalDate startsOn, LocalDate endsOn, Set<Surface> surfaces, boolean published) {
        return valid()
                .startsOn(startsOn)
                .endsOn(endsOn)
                .surfaces(surfaces)
                .published(published)
                .build();
    }

    /** 어느 사유로 거절됐는지까지 본다 — 예외 타입만 보면 다른 규칙에 걸려도 초록이 뜬다. */
    private static ErrorCode errorCodeOf(Executable executable) {
        return assertThrows(CurationException.class, executable).errorCode();
    }

    // ── 링크 주소 ─────────────────────────────────────────────────────────

    /**
     * 웹뷰가 임의 주소를 여는 통로가 된다. {@code javascript:} 처럼 스킴 자체가 다른 뜻인 것도 여기서 끊긴다.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "http://ktostay.visitkorea.or.kr",
        "javascript:alert(1)",
        "ktostay.visitkorea.or.kr",
        "//ktostay.visitkorea.or.kr",
    })
    void https_가_아닌_주소는_거절한다(String url) {
        assertEquals(
                CurationErrorCode.INSECURE_LINK_URL,
                errorCodeOf(() -> valid().linkUrl(url).build()));
    }

    /**
     * 접두어 비교로는 못 잡던 것들이다. 스킴만 맞고 <b>갈 곳이 없는</b> 주소라, 앱에서 칩은 보이는데 눌러도
     * 아무 일이 안 일어난다 — 거절보다 나쁜 상태다.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "https://",
        "https:///path",
        "https://  /path",
    })
    void 스킴만_맞고_주소가_없으면_거절한다(String url) {
        assertEquals(
                CurationErrorCode.INSECURE_LINK_URL,
                errorCodeOf(() -> valid().linkUrl(url).build()));
    }

    /** RFC 3986 의 scheme 은 대소문자를 구분하지 않는다. 정상 주소를 거절하면 어드민이 이유를 못 찾는다. */
    @Test
    void 스킴이_대문자여도_정상_https_주소다() {
        assertEquals(
                "HTTPS://ktostay.visitkorea.or.kr",
                valid().linkUrl("HTTPS://ktostay.visitkorea.or.kr").build().getLinkUrl());
    }

    @Test
    void 썸네일도_https_가_아니면_거절한다() {
        assertEquals(
                CurationErrorCode.INSECURE_LINK_URL,
                errorCodeOf(() -> valid().thumbnailUrl("http://a.example/t.jpg").build()));
    }

    @Test
    void 썸네일은_없어도_된다() {
        assertEquals(null, valid().build().getThumbnailUrl());
    }

    // ── 기간 규칙 (#217 의 교훈) ────────────────────────────────────────────

    /**
     * 날짜를 비운 것이 "상시" 인지 "깜빡한 것" 인지 값만 보고 알 수 없는 것이 policy 가 덴 자리다.
     * 상시를 명시적 플래그로 받고, 끄면 종료일을 요구한다.
     */
    @Test
    void 상시가_아닌데_종료일이_없으면_거절한다() {
        assertEquals(
                CurationErrorCode.END_DATE_REQUIRED,
                errorCodeOf(() -> link(STARTS, null, Set.of(Surface.HOME), true)));
    }

    @Test
    void 상시면_종료일이_없어도_된다() {
        CuratedLink always = valid().endsOn(null).alwaysOn(true).build();

        assertTrue(always.activeOn(LocalDate.of(2030, 12, 31)));
        assertTrue(always.activeOn(LocalDate.of(2020, 1, 1)));
    }

    @Test
    void 종료일이_시작일보다_앞서면_거절한다() {
        assertEquals(
                CurationErrorCode.PERIOD_REVERSED,
                errorCodeOf(() -> link(ENDS, STARTS, Set.of(Surface.HOME), true)));
    }

    @Test
    void 시작일과_종료일이_같은_하루짜리는_허용한다() {
        assertTrue(link(STARTS, STARTS, Set.of(Surface.HOME), true).activeOn(STARTS));
    }

    /** 경계 양끝을 포함한다 — 마지막 날 아침에 사라지면 사용자는 버그로 읽는다. */
    @ParameterizedTest
    @CsvSource({
        "2026-07-31, false",
        "2026-08-01, true",
        "2026-08-15, true",
        "2026-08-31, true",
        "2026-09-01, false",
    })
    void 오늘이_기간_안일_때만_유효하다(LocalDate today, boolean expected) {
        assertEquals(expected, link(STARTS, ENDS, Set.of(Surface.HOME), true).activeOn(today));
    }

    /** 시작일이 없으면 "언제 시작했는지 모르지만 이미 시작했다" 로 읽는다. 종료일은 생성자가 이미 요구했다. */
    @Test
    void 시작일이_없으면_종료일까지_유효하다() {
        CuratedLink noStart = link(null, ENDS, Set.of(Surface.HOME), true);

        assertTrue(noStart.activeOn(LocalDate.of(2020, 1, 1)));
        assertTrue(noStart.activeOn(ENDS));
        assertFalse(noStart.activeOn(ENDS.plusDays(1)));
    }

    // ── 칩 문구 · 면 ──────────────────────────────────────────────────────

    @Test
    void 칩_문구가_화면_길이를_넘으면_거절한다() {
        String tooLong = "가".repeat(CuratedLink.MAX_CHIP_TEXT_LENGTH + 1);

        assertEquals(
                CurationErrorCode.CHIP_TEXT_TOO_LONG,
                errorCodeOf(() -> valid().chipText(tooLong).build()));
    }

    @Test
    void 칩_문구가_경계_길이면_통과한다() {
        String exact = "가".repeat(CuratedLink.MAX_CHIP_TEXT_LENGTH);

        assertEquals(exact, valid().chipText(exact).build().getChipText());
    }

    /** 면을 하나도 안 고르면 아무 데도 안 나가는 항목이 만들어진다. 저장 시점에 막는다. */
    @Test
    void 내릴_화면을_하나도_안_고르면_거절한다() {
        assertEquals(
                CurationErrorCode.SURFACE_REQUIRED,
                errorCodeOf(() -> link(null, ENDS, Set.of(), true)));
    }

    // ── 노출 판정 ─────────────────────────────────────────────────────────

    /** 켠 면에서만 보인다. 네 면을 전수로 돈다 — 면이 늘면 이 테스트가 먼저 깨진다. */
    @ParameterizedTest
    @EnumSource(Surface.class)
    void 켜지_않은_면에서는_안_보인다(Surface surface) {
        CuratedLink onlyHome = link(STARTS, ENDS, Set.of(Surface.HOME), true);

        assertEquals(surface == Surface.HOME, onlyHome.visibleOn(surface, STARTS));
    }

    @ParameterizedTest
    @EnumSource(Surface.class)
    void 네_면을_다_켜면_어디서나_보인다(Surface surface) {
        CuratedLink everywhere =
                link(STARTS, ENDS, Set.of(Surface.HOME, Surface.REGION, Surface.COURSE, Surface.POI), true);

        assertTrue(everywhere.visibleOn(surface, STARTS));
    }

    /** 만들다 만 항목이 곧바로 사용자에게 보이면 안 된다 — 켜는 것은 명시적 행위여야 한다. */
    @Test
    void 게시하지_않았으면_기간_안이어도_안_보인다() {
        assertFalse(link(STARTS, ENDS, Set.of(Surface.HOME), false).visibleOn(Surface.HOME, STARTS));
    }

    @Test
    void 기간이_지났으면_게시했어도_안_보인다() {
        assertFalse(link(STARTS, ENDS, Set.of(Surface.HOME), true).visibleOn(Surface.HOME, ENDS.plusDays(1)));
    }
}
