package com.offway.core.policy.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 언제 알릴지 정하는 규칙(#220).
 *
 * <p><b>보내는 것만 확인하면 매일 보내는 구현도 통과한다.</b> 그래서 여기서 잠그는 것은 "보낸다" 가
 * 아니라 <b>"그날에만 보낸다"</b> 다 — 안 고쳐도 더 안 울리는 것이 이 알림이 살아남는 조건이다.
 *
 * <p>날짜를 고정해 시계에 안 묶이게 한다.
 */
class PolicyStalenessTest {

    private static final LocalDate 오늘 = LocalDate.of(2026, 9, 1);

    private static Policy 정책(LocalDate periodEnd, boolean verified, LocalDate checkedOn) {
        return Policy.builder()
                .type(PolicyType.STAY_FESTA)
                .name("숙박세일페스타")
                .periodEnd(periodEnd)
                .verified(verified)
                .checkedOn(checkedOn)
                .build();
    }

    /** 기간·검증·확인일이 전부 멀쩡한 정책 — 아무 날에도 안 걸려야 한다. */
    private static Policy 멀쩡한_정책(LocalDate periodEnd) {
        return 정책(periodEnd, true, 오늘.minusDays(1));
    }

    @ParameterizedTest
    @ValueSource(ints = {14, 7})
    void 정해진_예고일에만_종료를_알린다(int daysBefore) {
        Policy policy = 멀쩡한_정책(오늘.plusDays(daysBefore));

        assertEquals(Optional.of(PolicyStaleness.EXPIRING_SOON), PolicyStaleness.of(policy, 오늘));
    }

    /**
     * <b>이 테스트가 이 이슈의 핵심이다.</b> "14일 이하" 로 구현하면 만료까지 매일 울린다 — 안 고치면
     * 계속 오고, 며칠이면 아무도 안 본다. 그러면 알림이 없는 것과 같다.
     */
    @ParameterizedTest
    @ValueSource(ints = {30, 15, 13, 8, 6, 1})
    void 예고일이_아닌_날에는_조용하다(int daysBefore) {
        Policy policy = 멀쩡한_정책(오늘.plusDays(daysBefore));

        assertTrue(PolicyStaleness.of(policy, 오늘).isEmpty(), "남은 " + daysBefore + "일에 울렸다");
    }

    @Test
    void 종료_당일은_따로_알린다() {
        // 내일부터 뱃지가 사라진다. 예고와 같은 문구로 뭉치면 "아직 시간이 있다" 로 읽힌다.
        Policy policy = 멀쩡한_정책(오늘);

        assertEquals(Optional.of(PolicyStaleness.EXPIRES_TODAY), PolicyStaleness.of(policy, 오늘));
    }

    @Test
    void 이미_끝난_정책은_방치로_본다() {
        Policy policy = 멀쩡한_정책(오늘.minusDays(3));

        assertEquals(Optional.of(PolicyStaleness.EXPIRED), PolicyStaleness.of(policy, 오늘));
    }

    @Test
    void 미검증_정책은_화면에_안_나가므로_걸린다() {
        // 디지털관광주민증이 이 상태다 — 명단 미확보로 노출이 안 되는데 아무도 모른다.
        Policy policy = 정책(오늘.plusYears(1), false, 오늘);

        assertEquals(Optional.of(PolicyStaleness.UNVERIFIED), PolicyStaleness.of(policy, 오늘));
    }

    @Test
    void 확인한_지_오래되면_다시_보라고_한다() {
        // 기관 페이지는 개편이 잦아, 기간이 멀쩡해도 내용이 낡는다.
        Policy policy = 정책(오늘.plusYears(1), true, 오늘.minusDays(PolicyStaleness.STALE_CHECK_DAYS + 1));

        assertEquals(Optional.of(PolicyStaleness.STALE_CHECK), PolicyStaleness.of(policy, 오늘));
    }

    @Test
    void 확인_주기_경계에서는_아직_안_걸린다() {
        Policy policy = 정책(오늘.plusYears(1), true, 오늘.minusDays(PolicyStaleness.STALE_CHECK_DAYS));

        assertTrue(PolicyStaleness.of(policy, 오늘).isEmpty());
    }

    @Test
    void 확인일자를_모르면_낡았다고_단정하지_않는다() {
        // 이 컬럼이 생기기 전 행은 null 이다. 모르는 것을 낡음으로 읽으면 첫 실행에서 전부 울린다.
        Policy policy = 정책(오늘.plusYears(1), true, null);

        assertTrue(PolicyStaleness.of(policy, 오늘).isEmpty());
    }

    @Test
    void 상시_정책은_기간으로_걸리지_않는다() {
        // 종료일이 없으면 "상시" 다. 그걸 만료로 읽으면 멀쩡한 정책이 매번 알림에 실린다.
        Policy policy = 정책(null, true, 오늘);

        assertTrue(PolicyStaleness.of(policy, 오늘).isEmpty());
    }

    @Test
    void 사유가_겹치면_급한_쪽을_고른다() {
        // 오늘 끝나면서 확인일자도 낡은 정책. 한 정책이 두 줄로 뜨면 목록이 사람 눈에서 흐려진다.
        Policy policy = 정책(오늘, true, 오늘.minusDays(PolicyStaleness.STALE_CHECK_DAYS + 1));

        assertEquals(Optional.of(PolicyStaleness.EXPIRES_TODAY), PolicyStaleness.of(policy, 오늘));
    }

    @Test
    void 예고와_요약을_가른다() {
        // 예고는 날마다 돌고 방치는 주 1회다 — 이 구분이 틀리면 방치 알림이 매일 온다.
        assertTrue(PolicyStaleness.EXPIRING_SOON.isExpiryNotice());
        assertTrue(PolicyStaleness.EXPIRES_TODAY.isExpiryNotice());
        assertTrue(!PolicyStaleness.EXPIRED.isExpiryNotice());
        assertTrue(!PolicyStaleness.UNVERIFIED.isExpiryNotice());
        assertTrue(!PolicyStaleness.STALE_CHECK.isExpiryNotice());
    }
}
