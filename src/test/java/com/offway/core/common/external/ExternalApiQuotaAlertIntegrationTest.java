package com.offway.core.common.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.notification.Notifier;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 한도 알림(#257) — <b>한 단계에 한 번만 울리는가</b>.
 *
 * <p>{@code record()} 는 외부 호출마다 돈다. "70% 넘었다" 로 판정하면 그 뒤 모든 호출이 알림을 쏜다.
 * 놓치는 것보다 이쪽이 더 나쁘다 — 며칠이면 아무도 안 보게 되어 알림이 없는 것과 같아진다.
 */
@SpringBootTest
class ExternalApiQuotaAlertIntegrationTest {

    /** 한도 50 이라 5건마다 한 단계 — 경계까지 몰아가는 데 호출이 적게 든다. */
    private static final ExternalApi TIGHT_API = ExternalApi.TMAP_WAYPOINT;

    @Autowired
    private ExternalApiCallRecorder recorder;

    @Autowired
    private ExternalApiCallRepository repository;

    @Autowired
    private CapturingNotifier notifier;

    /**
     * 보낸 문구를 모으는 대체 구현.
     *
     * <p>디스코드는 외부 경계라 실제로 부르지 않는다. 그렇다고 아무것도 안 하면 "정말 나갔는가" 를 운영에
     * 올려 봐야 알게 된다.
     */
    static class CapturingNotifier implements Notifier {

        private final List<String> sent = new CopyOnWriteArrayList<>();

        @Override
        public void send(String message) {
            sent.add(message);
        }

        List<String> drain() {
            List<String> copy = new ArrayList<>(sent);
            sent.clear();
            return copy;
        }
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        CapturingNotifier capturingNotifier() {
            return new CapturingNotifier();
        }
    }

    @Test
    void 단계를_넘는_호출에서만_한_번_울린다() {
        LocalDate today = recorder.today();
        long used = repository.countsOn(today).getOrDefault(TIGHT_API, 0L);
        long boundary = nextBoundary(used);
        notifier.drain();

        // 경계 직전까지는 조용하다.
        for (long call = used; call < boundary - 1; call++) {
            recorder.record(TIGHT_API);
        }
        assertTrue(notifier.drain().isEmpty(), "단계를 안 넘었는데 울렸다");

        // 경계를 넘는 그 한 건에서만 울린다.
        recorder.record(TIGHT_API);
        assertEquals(1, notifier.drain().size());

        // 같은 단계 안에서는 몇 번을 더 불러도 조용하다.
        recorder.record(TIGHT_API);
        recorder.record(TIGHT_API);
        assertTrue(notifier.drain().isEmpty(), "같은 단계에서 다시 울렸다 — 호출마다 쏘면 아무도 안 본다");
    }

    @Test
    void 이미_알린_단계는_다시_선점되지_않는다() {
        // 재배포해도 이미 보낸 단계를 다시 보내지 않는 근거. 인메모리 플래그였다면 여기서 true 가 된다.
        LocalDate day = LocalDate.of(2099, 1, 2);
        repository.recordAndCount(ExternalApi.TOUR_GALLERY, day);

        assertTrue(repository.claimNotifyStep(ExternalApi.TOUR_GALLERY, day, 3));
        assertFalse(repository.claimNotifyStep(ExternalApi.TOUR_GALLERY, day, 3));
    }

    @Test
    void 더_높은_단계는_다시_선점된다() {
        // 30% 를 알린 뒤 40% 에 닿으면 그건 새 소식이다.
        LocalDate day = LocalDate.of(2099, 1, 3);
        repository.recordAndCount(ExternalApi.TOUR_GALLERY, day);
        repository.claimNotifyStep(ExternalApi.TOUR_GALLERY, day, 3);

        assertTrue(repository.claimNotifyStep(ExternalApi.TOUR_GALLERY, day, 4));
    }

    @Test
    void 날짜가_다르면_알림_단계도_따로다() {
        // call_date 가 키라 자정을 넘기면 새 행이 된다 — 단계 초기화 작업이 따로 없다.
        LocalDate day = LocalDate.of(2099, 1, 4);
        LocalDate nextDay = day.plusDays(1);
        repository.recordAndCount(ExternalApi.TOUR_GALLERY, day);
        repository.recordAndCount(ExternalApi.TOUR_GALLERY, nextDay);
        repository.claimNotifyStep(ExternalApi.TOUR_GALLERY, day, 5);

        assertTrue(repository.claimNotifyStep(ExternalApi.TOUR_GALLERY, nextDay, 5));
    }

    /** {@code used} 다음에 오는 단계 경계의 누적 호출 수. */
    private static long nextBoundary(long used) {
        int nextStep = TIGHT_API.usageStep(used) + 1;
        return (long) nextStep * TIGHT_API.dailyLimit() / 10;
    }
}
