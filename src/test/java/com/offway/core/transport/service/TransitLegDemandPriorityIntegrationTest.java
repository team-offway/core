package com.offway.core.transport.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.transport.domain.TransitLegDuration;
import com.offway.core.transport.domain.TransitMode;
import com.offway.core.transport.repository.TransitLegDurationRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배치가 <b>실제로 물어본 구간</b>부터 재는지(#491).
 *
 * <p>이 순서는 DB 가 판정한다 — 정렬이 쿼리에 있고, 시드 33,448건이 이미 들어와 있는 상태에서만
 * 문제가 드러난다. 그래서 단위가 아니라 통합으로 본다.
 *
 * <p>배경: 사전 적재(#450)가 후보를 부팅 시각으로 한꺼번에 넣으면서, 사용자가 방금 요청한 구간이
 * {@code requestedAt} 순으로는 3만 건 뒤에 섰다. 배치가 시간당 50구간이라 28일이 걸린다.
 */
@SpringBootTest
@Transactional
class TransitLegDemandPriorityIntegrationTest {

    /** 시드보다 훨씬 뒤에 요청된 구간이라도 맨 앞에 와야 한다 — 그게 이 변경의 전부다. */
    private static final int PAGE = 50;

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    @Autowired
    private TransitLegDurationRepository transitLegDurationRepository;

    @Autowired
    private TransitDurationService transitDurationService;

    /**
     * <b>시각을 시드보다 뒤로 잡는 것이 이 테스트의 전부다.</b> 부팅 시각보다 이른 시각을 쓰면 옛 정렬
     * ({@code requestedAt} 오름차순)로도 앞에 서서, 고쳤는지 안 고쳤는지를 못 가른다.
     */
    @Test
    void 방금_물어본_구간이_시드_후보보다_먼저_측정된다() {
        LocalDateTime afterSeeding = LocalDateTime.now(SERVICE_ZONE).plusHours(1);
        transitLegDurationRepository.requestIfAbsent(TransitLegDuration.requested(
                TransitMode.INTERCITY_BUS, "TESTDEP1", "TESTARR1", afterSeeding));

        List<TransitLegDuration> pending = transitDurationService.pending(PAGE, afterSeeding.minusDays(90));

        assertEquals(
                "TESTDEP1", pending.getFirst().getDepCode(),
                "부팅 때 넣은 시드 3만 건 뒤에 서면 배치 주기로 28일을 기다린다");
    }

    @Test
    void 시드로_들어간_구간도_사용자가_물으면_앞으로_당겨진다() {
        // 시드 시각도 부팅보다 뒤로 잡는다. 앞으로 잡으면 requestedAt 만으로도 1등이 되어,
        // 물어본 표시가 붙었는지 안 붙었는지를 못 가른다(실제로 그렇게 통과하고 있었다).
        LocalDateTime seededAt = LocalDateTime.now(SERVICE_ZONE).plusHours(1);
        LocalDateTime askedAt = seededAt.plusMinutes(1);
        transitLegDurationRepository.requestIfAbsent(TransitLegDuration.seeded(
                TransitMode.INTERCITY_BUS, "TESTDEP2", "TESTARR2", seededAt));

        // 코스가 이 구간을 묻는다 — 값이 없으니 순번이 올라가야 한다.
        transitDurationService.minutesFor(TransitMode.INTERCITY_BUS, "TESTDEP2", "TESTARR2", askedAt);

        List<TransitLegDuration> pending = transitDurationService.pending(PAGE, askedAt.minusDays(90));

        assertEquals(
                "TESTDEP2", pending.getFirst().getDepCode(),
                "시드로 이미 들어가 있으면 아무리 물어도 순번이 안 올라가던 자리다");
    }

    @Test
    void 아무도_안_물어본_시드는_뒤에_선다() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 7, 2, 0);
        transitLegDurationRepository.requestIfAbsent(TransitLegDuration.seeded(
                TransitMode.INTERCITY_BUS, "TESTDEP3", "TESTARR3", now.minusYears(1)));
        transitLegDurationRepository.requestIfAbsent(TransitLegDuration.requested(
                TransitMode.INTERCITY_BUS, "TESTDEP4", "TESTARR4", now));

        List<TransitLegDuration> pending = transitDurationService.pending(PAGE, now.minusDays(90));

        int askedAt = indexOf(pending, "TESTDEP4");
        int seededAt = indexOf(pending, "TESTDEP3");
        assertTrue(askedAt >= 0, "물어본 구간이 첫 페이지에 있어야 한다");
        assertTrue(
                seededAt < 0 || askedAt < seededAt,
                "시드가 1년 더 오래 기다렸어도 물어본 쪽이 먼저다 — 화면에 값이 없는 건 그쪽이다");
    }

    @Test
    void 물어본_것끼리는_최근에_물은_쪽이_먼저다() {
        LocalDateTime older = LocalDateTime.of(2026, 9, 5, 2, 0);
        LocalDateTime newer = LocalDateTime.of(2026, 9, 7, 2, 0);
        transitLegDurationRepository.requestIfAbsent(TransitLegDuration.requested(
                TransitMode.INTERCITY_BUS, "TESTDEP5", "TESTARR5", older));
        transitLegDurationRepository.requestIfAbsent(TransitLegDuration.requested(
                TransitMode.INTERCITY_BUS, "TESTDEP6", "TESTARR6", newer));

        List<TransitLegDuration> pending = transitDurationService.pending(PAGE, newer.minusDays(90));

        assertEquals(
                "TESTDEP6",
                pending.stream()
                        .map(TransitLegDuration::getDepCode)
                        .filter(code -> code.startsWith("TESTDEP"))
                        .findFirst()
                        .orElse(null),
                "최근 수요가 지금 화면에 값이 없다는 뜻이다");
    }

    private static int indexOf(List<TransitLegDuration> legs, String depCode) {
        for (int i = 0; i < legs.size(); i++) {
            if (depCode.equals(legs.get(i).getDepCode())) {
                return i;
            }
        }
        return -1;
    }
}
