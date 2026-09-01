package com.offway.core.policy.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 정책이 스스로 지키는 불변식(#344).
 *
 * <p><b>지금까지 검증할 자리가 없었다.</b> seed SQL 이 넣고 JPA 가 읽는 것이 전부라 코드에서 만들 일이
 * 없었다. 백오피스가 붙으면서 <b>사람이 임의의 값을 넣는 경로</b>가 생겼고, 여기가 그 최후의 보루다.
 */
class PolicyTest {

    private static Policy.PolicyBuilder valid() {
        return Policy.builder()
                .type(PolicyType.STAY_FESTA)
                .name("2026 대한민국 숙박세일 페스타")
                .periodStart(LocalDate.of(2026, 6, 11))
                .periodEnd(LocalDate.of(2026, 8, 31))
                .applyUrl("https://ktostay.visitkorea.or.kr")
                .verified(true);
    }

    // ── 기간 ──────────────────────────────────────────────────────────────

    @Test
    void 시작일이_종료일보다_늦으면_거절한다() {
        // 거꾸로 넣으면 isActiveOn 이 어떤 날짜에도 참이 아니라 뱃지가 영영 안 뜬다. 저장은 성공하고
        // 화면에도 값이 그대로 보이므로, 막지 않으면 "등록했는데 왜 안 나오지" 가 된다.
        Policy.PolicyBuilder reversed = valid()
                .periodStart(LocalDate.of(2026, 8, 31))
                .periodEnd(LocalDate.of(2026, 6, 11));

        PolicyException exception = assertThrows(PolicyException.class, reversed::build);
        assertEquals(PolicyErrorCode.INVALID_PERIOD, exception.errorCode());
    }

    @Test
    void 같은_날_시작하고_끝나는_것은_정상이다() {
        LocalDate oneDay = LocalDate.of(2026, 6, 11);

        assertTrue(valid().periodStart(oneDay).periodEnd(oneDay).build().isActiveOn(oneDay));
    }

    @Test
    void 한쪽만_있는_기간은_정상이다() {
        // 시작만 있으면 "그날부터 상시", 종료만 있으면 "그날까지" 다.
        assertTrue(valid().periodEnd(null).build().isActiveOn(LocalDate.of(2030, 1, 1)));
        assertTrue(valid().periodStart(null).build().isActiveOn(LocalDate.of(2020, 1, 1)));
    }

    // ── 신청 주소 ─────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"http://ktostay.visitkorea.or.kr", "ftp://x.kr", "그냥 문자열", "https://"})
    void https_가_아니거나_호스트가_없으면_거절한다(String applyUrl) {
        // "https://" 는 접두사 비교만 하면 통과한다 — 앱이 웹뷰로 열면 아무 데도 안 간다.
        PolicyException exception =
                assertThrows(PolicyException.class, () -> valid().applyUrl(applyUrl).build());
        assertEquals(PolicyErrorCode.INSECURE_APPLY_URL, exception.errorCode());
    }

    @Test
    void 스킴은_대소문자를_가리지_않는다() {
        // 접두사 비교로 짰다면 이것이 거절된다. 스킴은 대소문자 무관이라 정상 주소다.
        assertEquals("HTTPS://ktostay.visitkorea.or.kr", valid()
                .applyUrl("HTTPS://ktostay.visitkorea.or.kr")
                .build()
                .getApplyUrl());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 신청_주소는_비워도_된다(String applyUrl) {
        // 신청 페이지가 없는 정책이 있고, 그때 뱃지는 눌리지 않는 안내로 남는다.
        assertNull(valid().applyUrl(applyUrl).build().getApplyUrl());
    }

    // ── 필수값·정규화 ─────────────────────────────────────────────────────

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 정책명은_비울_수_없다(String name) {
        assertThrows(RuntimeException.class, () -> valid().name(name).build());
    }

    @Test
    void 빈_문자열은_null_로_저장한다() {
        // 앱은 null 이면 그 줄을 안 그린다. 빈 문자열로 두면 빈 줄이 생긴다.
        Policy policy = valid().benefitDetail("  ").targetAudience("").periodNote("   ").build();

        assertNull(policy.getBenefitDetail());
        assertNull(policy.getTargetAudience());
        assertNull(policy.getPeriodNote());
    }

    // ── 수정은 생성과 같은 규칙을 탄다 ────────────────────────────────────

    @Test
    void 고칠_때도_같은_검증을_받는다() {
        Policy policy = valid().build();

        // 만들 때는 막히고 고칠 때는 통과하면, 어드민이 저장 한 번으로 규칙을 우회한다.
        assertThrows(PolicyException.class, () -> policy.update(
                PolicyType.STAY_FESTA, "이름", null, null,
                LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1),
                null, null, true, null, "박세빈"));
    }

    @Test
    void 고치면_감사_흔적이_남는다() {
        Policy policy = valid().build();

        policy.update(PolicyType.STAY_FESTA, "새 이름", null, null, null, null, null, null, true, null, "박세빈");

        assertEquals("박세빈", policy.getUpdatedBy());
        assertEquals("새 이름", policy.getName());
    }

    // ── 뱃지 겹침 ─────────────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
        // 겹친다 — 같은 분류면 같은 뱃지가 둘 뜬다
        "2026-08-01, 2026-09-30, true",
        "2026-06-11, 2026-08-31, true",
        "2026-08-31, 2026-12-31, true",
        // 안 겹친다
        "2026-09-01, 2026-12-31, false",
        "2026-01-01, 2026-06-10, false",
    })
    void 기간이_겹치는지_판정한다(LocalDate otherStart, LocalDate otherEnd, boolean expected) {
        Policy policy = valid().build(); // 2026-06-11 ~ 08-31

        assertEquals(expected, policy.periodOverlaps(otherStart, otherEnd));
    }

    @Test
    void 날짜가_없는_쪽은_상시라_무엇과도_겹친다() {
        // isActiveOn 이 null 을 "상시" 로 읽기 때문이다 — 겹침 판정도 같은 기준이어야 한다.
        Policy always = valid().periodStart(null).periodEnd(null).build();

        assertTrue(always.periodOverlaps(LocalDate.of(2030, 1, 1), LocalDate.of(2030, 12, 31)));
        assertTrue(valid().build().periodOverlaps(null, null));
    }

    @Test
    void 종료일이_없으면_그_뒤_전부와_겹친다() {
        Policy openEnded = valid().periodEnd(null).build(); // 2026-06-11 ~ 끝없음

        assertTrue(openEnded.periodOverlaps(LocalDate.of(2030, 1, 1), LocalDate.of(2030, 12, 31)));
        assertFalse(openEnded.periodOverlaps(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31)));
    }
}
