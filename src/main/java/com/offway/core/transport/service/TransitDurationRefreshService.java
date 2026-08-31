package com.offway.core.transport.service;

import com.offway.core.transport.domain.MeasuredLeg;
import com.offway.core.transport.domain.TransitLegDuration;
import com.offway.core.transport.domain.TransitLegResult;
import com.offway.core.transport.infrastructure.tago.TransitLegClient;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 아직 안 잰 구간을 채우는 배치(#107 · #97). 코스가 물었지만 값이 없어 자리만 만들어 둔 구간을 실호출로 잰다.
 *
 * <h2>왜 배치인가</h2>
 *
 * <p>요청 경로에서 부를 수 없다. 구간 조회는 외부 호출이고, 코스 하나가 여러 구간을 물으면 그만큼 응답이
 * 느려진다(CLAUDE.md §요청 경로에서 외부 I/O 를 뺀다). 첫 요청은 소요시간 없이 나가고, 그다음부터 정확하다.
 *
 * <h2>한도를 어떻게 지키나</h2>
 *
 * <p>{@code fixedDelay} 가 아니라 <b>cron</b> 이다. {@code fixedDelay} 는 프로세스가 살아 있는 동안의
 * 간격이라 재배포마다 주기가 처음부터 다시 센다 — "한 시간에 한 번" 이라 적어 두고 배포할 때마다 도는 일이
 * 실제로 있었다(#226 · #231).
 *
 * <p>상한은 <b>구간 수가 아니라 호출 수</b>({@value #MAX_CALLS_PER_RUN})로 센다. 조회창이 수단마다 달라
 * (버스 3일 · 여객선 8일) 구간 수로 세면 여객선이 몰린 회차에 한도가 두 배 넘게 나간다. 시간당 한 번이면
 * 하루 최대 1,440 건 — TAGO 한도 10,000 의 15% 다. 예산을 다 쓰면 남은 구간은 다음 회차가 이어받는다.
 *
 * <h2>왜 하루만 보지 않는가</h2>
 *
 * <p>주 3회만 뜨는 항로가 있다. 오늘 하루만 물어 비면 "이 구간은 안 다닌다" 로 굳어 버리는데, 그건 틀린
 * 결론이면서 되돌릴 계기도 없다. 조회창 안에서 며칠을 밀어 보고, 그래도 없을 때만 미운행으로 적는다.
 *
 * <p>그렇게 적은 미운행도 {@value #REMEASURE_DAYS} 일 뒤에는 다시 잰다. 계절에만 뜨는 항로와 새로 뚫린
 * 노선이 한 번의 조회로 영원히 없는 길이 되지 않게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransitDurationRefreshService {

    /** 회당 가져올 구간 수 — 호출 예산을 다 못 쓰고 남으면 다음 회차가 이어받는다. */
    private static final int MAX_LEGS_PER_RUN = 20;

    /** 회당 외부 호출 상한 — 한도 보호. 구간 하나에 조회창만큼 나가므로 구간 수로는 셀 수 없다. */
    private static final int MAX_CALLS_PER_RUN = 60;

    /** 미운행으로 적힌 구간을 다시 재기까지의 기간. */
    private static final int REMEASURE_DAYS = 30;

    private static final Period REMEASURE_AFTER = Period.ofDays(REMEASURE_DAYS);

    /** 여행도 배차도 한국 기준이다 — 서버 기본 시간대에 기대지 않는다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final TransitDurationService transitDurationService;
    private final TransitLegClient transitLegClient;

    /** 매시 17분 — 정각에 몰린 다른 배치와 겹치지 않게 어긋냈다. */
    @Scheduled(cron = "0 17 * * * *", zone = "Asia/Seoul")
    public void measurePending() {
        LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
        List<TransitLegDuration> pending =
                transitDurationService.pending(MAX_LEGS_PER_RUN, now.minus(REMEASURE_AFTER));
        if (pending.isEmpty()) {
            return;
        }
        LocalDate today = now.toLocalDate();
        int measured = 0;
        int noService = 0;
        int unavailable = 0;
        int calls = 0;
        int skipped = 0;
        for (TransitLegDuration leg : pending) {
            if (calls >= MAX_CALLS_PER_RUN) {
                skipped++; // 예산이 말랐다. 남은 구간은 다음 회차가 잰다
                continue;
            }
            Attempt attempt = measure(leg, today, MAX_CALLS_PER_RUN - calls);
            calls += attempt.calls();
            switch (attempt.result()) {
                case TransitLegResult.Measured(MeasuredLeg found) -> {
                    record(leg, found);
                    measured++;
                }
                case TransitLegResult.NoService ignored -> {
                    // 이것도 결과다 — 적어야 같은 구간을 매 시간 다시 재지 않는다. 다만 영구는 아니라,
                    // REMEASURE_DAYS 가 지나면 다시 대상이 된다.
                    record(leg, null);
                    noService++;
                }
                case TransitLegResult.Unavailable ignored -> unavailable++; // 적지 않는다. 다음 배치가 다시 잰다
            }
        }
        // 0건이어도 남긴다 — 배치가 돌았는지, 왜 0건인지 답할 수 있어야 한다(#310).
        // 셋을 갈라서 남기는 이유: 확보 0건일 때 "다 미운행" 인지 "다 조회 실패" 인지가 대응을 가른다.
        // 예산에 걸려 넘긴 수도 함께 남긴다 — 밀린 구간이 쌓이는 것을 로그만 보고 알 수 있어야 한다.
        log.info("구간 소요시간 측정 — 대상 {}건 · 호출 {}건: 확보 {} · 미운행 {} · 조회불가 {} · 예산초과로 넘김 {}",
                pending.size(), calls, measured, noService, unavailable, skipped);
    }

    private void record(TransitLegDuration leg, MeasuredLeg found) {
        transitDurationService.record(
                leg.getMode(), leg.getDepCode(), leg.getArrCode(), found, LocalDateTime.now(SERVICE_ZONE));
    }

    /**
     * 조회창 안에서 날짜를 밀어 가며 첫 결과를 찾는다. <b>며칠까지 미는지는 수단이 정한다</b> —
     * 버스는 오늘~+2일, 여객선은 오늘~+7일이다. 여객선을 버스에 맞춰 자르면 주 몇 편짜리 항로가 미운행으로
     * 굳고, 그 지역은 닿는 수단이 하나도 없게 된다.
     *
     * <p>순차다. 앞이 성공하면 뒤를 안 부르므로 병렬로 얻을 것이 없다 — 오히려 실패 구간에서 쓸데없는
     * 호출을 더 하게 된다.
     *
     * <p><b>조회 불가를 만나면 즉시 멈춘다.</b> 키가 없거나 한도가 말랐는데 날짜만 밀어 더 부르면 같은
     * 이유로 다 실패한다 — 한도만 태우고 결론은 같다. 그리고 그 뒤에 "미운행" 이라 적으면 멀쩡한 구간이
     * 굳는다.
     *
     * @param budget 이 구간에 쓸 수 있는 남은 호출 수 — 회당 상한이 호출 하나까지 내려와야 상한을 넘지 않는다
     */
    private Attempt measure(TransitLegDuration leg, LocalDate today, int budget) {
        int window = leg.getMode().lookaheadDays();
        int tries = Math.min(window, budget);
        for (int dayOffset = 0; dayOffset < tries; dayOffset++) {
            TransitLegResult result = transitLegClient.measure(
                    leg.getMode(), leg.getDepCode(), leg.getArrCode(), today.plusDays(dayOffset));
            if (!(result instanceof TransitLegResult.NoService)) {
                return new Attempt(result, dayOffset + 1);
            }
        }
        if (tries < window) {
            // 예산이 조회창을 다 못 돌았다. 여기서 미운행으로 적으면 못 본 날에 뜨는 편까지 없는 것이 된다 —
            // 불완전한 결과로 결론짓느니 버리고 다음 회차에 다시 잰다(CLAUDE.md §상한에 걸려 중단할 땐).
            log.debug("{} 예산이 조회창을 못 채워 미룬다 {}→{} {}/{}일",
                    leg.getMode().label(), leg.getDepCode(), leg.getArrCode(), tries, window);
            return new Attempt(new TransitLegResult.Unavailable(), tries);
        }
        // 조회창 안 모든 날짜가 정상 응답 + 편 없음 — 그 구간은 다니지 않는다.
        log.debug("{} 미운행으로 기록 {}→{}", leg.getMode().label(), leg.getDepCode(), leg.getArrCode());
        return new Attempt(new TransitLegResult.NoService(), tries);
    }

    /** 구간 하나를 잰 결과와 <b>그러느라 쓴 호출 수</b>. 뒤엣것이 있어야 회당 상한을 호출로 셀 수 있다. */
    private record Attempt(TransitLegResult result, int calls) {}
}
