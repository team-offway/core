package com.offway.core.policy.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 알림 한 통을 어떻게 묶는가(#220).
 *
 * <p>받는 사람이 <b>그 줄만 보고 바로 확인하러 갈 수 있어야</b> 한다. "확인 부탁" 만 오면 출처를 다시
 * 찾아야 하고, 그러면 알림이 일을 만들기만 한다.
 */
class PolicyAlertTest {

    private static final LocalDate 오늘 = LocalDate.of(2026, 9, 1);

    private static Policy 정책(String name, LocalDate periodEnd, boolean verified, LocalDate checkedOn) {
        return Policy.builder()
                .type(PolicyType.STAY_FESTA)
                .name(name)
                .periodEnd(periodEnd)
                .applyUrl("https://ktostay.visitkorea.or.kr")
                .verified(verified)
                .checkedOn(checkedOn)
                .build();
    }

    @Test
    void 보낼_것이_없으면_알림을_만들지_않는다() {
        // "오늘은 없음" 을 보내는 것도 알림 피로다. 조용한 날이 정상이라는 것을 채널이 스스로 말해야 한다.
        assertEquals(Optional.empty(), PolicyAlert.of("정책 종료 예고", List.of(), 오늘));
    }

    @Test
    void 여러_건을_한_통으로_묶는다() {
        // 정책마다 따로 보내면 그 자체가 소음이다.
        List<PolicyAlert.Entry> entries = List.of(
                new PolicyAlert.Entry(정책("숙박세일페스타", 오늘.plusDays(14), true, 오늘), PolicyStaleness.EXPIRING_SOON),
                new PolicyAlert.Entry(정책("반값여행", 오늘.plusDays(7), true, 오늘), PolicyStaleness.EXPIRING_SOON));

        PolicyAlert alert = PolicyAlert.of("정책 종료 예고", entries, 오늘).orElseThrow();

        assertEquals(2, alert.lines().size());
        assertTrue(alert.message().contains("정책 종료 예고 2건"), alert.message());
        assertTrue(alert.message().contains("숙박세일페스타"), alert.message());
        assertTrue(alert.message().contains("반값여행"), alert.message());
    }

    @Test
    void 한_줄에_사유와_기한과_확인일과_주소가_함께_있다() {
        PolicyAlert.Entry entry =
                new PolicyAlert.Entry(정책("숙박세일페스타", 오늘.plusDays(14), true, 오늘.minusDays(4)), PolicyStaleness.EXPIRING_SOON);

        String line = entry.describe(오늘);

        assertTrue(line.contains("숙박세일페스타"), line);
        assertTrue(line.contains("2026-09-15"), line); // 언제 끝나는지
        assertTrue(line.contains("14일 남음"), line); // 얼마나 급한지
        assertTrue(line.contains("확인 2026-08-28"), line); // 언제 적 값인지
        assertTrue(line.contains("https://ktostay.visitkorea.or.kr"), line); // 어디서 확인하는지
    }

    @Test
    void 확인한_적이_없으면_그렇게_적는다() {
        // 빈칸으로 두면 "확인일이 오늘" 인지 "기록이 없는" 것인지 읽는 사람이 구분할 수 없다.
        PolicyAlert.Entry entry =
                new PolicyAlert.Entry(정책("디지털관광주민증", null, false, null), PolicyStaleness.UNVERIFIED);

        assertTrue(entry.describe(오늘).contains("확인 기록 없음"), entry.describe(오늘));
    }

    @Test
    void 예고와_방치를_따로_모은다() {
        // 이 구분이 틀리면 고칠 때까지 계속 걸리는 방치 건이 매일 온다.
        List<Policy> policies = List.of(
                정책("곧 끝남", 오늘.plusDays(7), true, 오늘),
                정책("이미 끝남", 오늘.minusDays(3), true, 오늘),
                정책("미검증", null, false, 오늘));

        assertEquals(1, PolicyAlert.entriesOf(policies, 오늘, true).size(), "예고는 곧 끝나는 것만");
        assertEquals(2, PolicyAlert.entriesOf(policies, 오늘, false).size(), "요약은 끝난 것과 미검증");
    }

    @Test
    void 걸리지_않는_정책은_어느_쪽에도_안_담긴다() {
        List<Policy> policies = List.of(정책("멀쩡함", 오늘.plusDays(30), true, 오늘));

        assertTrue(PolicyAlert.entriesOf(policies, 오늘, true).isEmpty());
        assertTrue(PolicyAlert.entriesOf(policies, 오늘, false).isEmpty());
    }
}
