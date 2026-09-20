package com.offway.core.transport.service;

import com.offway.core.common.batch.domain.ManualBatch;
import com.offway.core.common.batch.service.RunningBatches;
import com.offway.core.common.batch.repository.BatchRunRepository;
import com.offway.core.common.external.Caller;
import com.offway.core.common.external.CallerContext;
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
 * 하루 최대 <b>3,600 건 — TAGO 한도 10,000 의 36%</b> 다. 예산을 다 쓰면 남은 구간은 다음 회차가 이어받는다.
 *
 * <p>이 숫자는 <b>한 번 낡았다.</b> #450 에서 회차당 구간을 20 에서 50 으로 올렸는데 여기 적힌 값은
 * 그대로였다(1,440 건 · 15%). 실제로는 그때 이미 2.5 배였고, 한도의 3분의 1 을 태우는 배치가 문서상
 * 15% 로 보였다. 상한을 만지면 이 줄을 같이 고친다.
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
public class TransitDurationRefreshService implements ManualBatch {

    /** 관리자 화면이 마지막 실행 시각을 붙이는 키(#537). batch_run.name 과 같은 값이어야 한다. */
    static final String BATCH_NAME = "transit-duration-refresh";

    /**
     * 이 배치가 태운 외부 호출에 붙는 이름(#285). 알림에 그대로 실리므로 사람이 읽는 말로 둔다.
     *
     * <p><b>없어서 한도의 36% 가 안 보였다</b>(#594). 맥락을 안 심으면 {@code Caller.UNKNOWN}("미상")
     * 으로 적히는데, 이 배치가 그 상태로 매시 150 콜을 태웠다 — 디스코드 한도 알림 둘째 줄이 계속
     * {@code 미상 3000} 이었고, 그게 배치인지 코스 생성인지 알림만 보고는 답할 수 없었다.
     */
    private static final Caller CALLER = Caller.of("구간소요시간배치");

    /**
     * 회당 가져올 구간 수 — 호출 예산을 다 못 쓰고 남으면 다음 회차가 이어받는다.
     *
     * <p><b>20 에서 올렸다</b>(#450). 후보를 미리 만들어 두면서 잴 것이 4건에서 3만여 건이 됐다.
     * 20 이면 하루 480 구간이라 재측정 주기(30일)를 못 따라간다 — 밀린 구간이 계속 쌓인다.
     */
    private static final int MAX_LEGS_PER_RUN = 50;

    /**
     * 회당 외부 호출 상한 — 한도 보호. 구간 하나에 조회창만큼 나가므로 구간 수로는 셀 수 없다.
     *
     * <p>버스 조회창이 3일이라 {@value #MAX_LEGS_PER_RUN} 구간이면 최대 150 호출이다. 하루 24회차로
     * 3,600 이고, TAGO 한도 10,000 의 36% 다. <b>남은 것은 사용자 요청의 열차 조회 몫이다</b> — 같은
     * 키를 쓰므로 여기서 다 태우면 코스 생성이 먼저 죽는다.
     */
    private static final int MAX_CALLS_PER_RUN = 150;

    /**
     * 미운행으로 적힌 구간을 다시 재기까지의 기간.
     *
     * <p><b>30 에서 늘렸고</b>(#450), <b>실측으로 확정했다</b>(#469). 아래 숫자는 2026-09-14 운영 DB 다.
     *
     * <h2>처리량이 주기를 정한다</h2>
     *
     * <p>이 배치의 상한은 시간당 {@value #MAX_LEGS_PER_RUN} 구간, 하루 24회차로 <b>1,200 구간</b>이다.
     * 실측 속도가 정확히 거기 붙어 있다(9/7~9/13 하루 1,190~1,200). 그러면 주기별로 필요한 양이 이렇다.
     *
     * <p>재측정 대상을 <b>44,260 건</b>으로 본다 — 전체 44,931 구간에 잰 것의 미운행 비율
     * 98.5%(10,099/10,253)를 그대로 적용한 값이다. 호출 수는 <b>버스 조회창 3일</b> 기준이고
     * ({@code TransitMode.lookaheadDays}), 여객선은 8일이지만 후보가 2건뿐이라 합계에 안 보인다.
     *
     * <pre>
     *              하루 필요    용량(1,200) 대비   TAGO 호출/일   한도(10,000) 대비
     *   30일        1,475          123% ✗           4,425            44%
     *   60일          738           62%             2,214            22%
     *   90일          492           41%             1,476            15%
     * </pre>
     *
     * <p><b>30일은 용량을 넘는다.</b> 하루 1,475 구간이 필요한데 상한이 1,200 이다 — 상한을 올리지
     * 않으면 영구히 밀리고, 올리면 TAGO 한도의 44% 를 배치가 먹는다. <b>그 키는 열차와 공유해서</b>
     * 거기서 태우면 사용자 코스 생성이 먼저 죽는다.
     *
     * <h2>대부분은 실재하지 않는 조합이다</h2>
     *
     * <p>후보가 전국 터미널 × 89곳 도착지라 죽은 조합이 많다. 잰 10,253 건 중 <b>운행이 있는 것은 154 건
     * (1.5%)</b> 이다. 반면 <b>사용자가 실제로 물어본 204 건에서는 29 건(14%)</b> 이 살아 있다 — 쓰이는
     * 구간과 시드 조합은 성질이 다르고, 주기를 짧게 잡으면 그 차이만큼 죽은 조합을 되묻는 데 쓴다.
     *
     * <h2>성공+빈 응답을 미운행으로 믿는다</h2>
     *
     * <p>규약의 "빈 응답을 성공으로 캐시하지 않는다" 와 부딪히는 자리라 따로 정했다(#469). 조회 실패·
     * 타임아웃·키 없음·비정상 {@code resultCode} 는 <b>기록하지 않으므로</b> 다음 회차가 다시 잰다.
     *
     * <p>그리고 문턱이 하루가 아니다 — {@link #measure} 는 <b>조회창 안 모든 날짜</b>가 성공+빈 응답일
     * 때만 미운행으로 적는다(버스 3일·여객선 8일). 하루라도 편이 뜨면 그 값을 쓰고, 예산이 조회창을 못
     * 채우면 아예 기록하지 않는다. 90일이 걸리는 것은 <b>그 창을 전부 물어 전부 비었을 때</b> 뿐이다.
     *
     * <p><b>아직 못 잰 것 하나가 남아 있다.</b> 첫 측정이 2026-08-31 이라 재측정이 한 건도 안 돌았다
     * (실측: 다시 물은 구간 0건). 미운행이 나중에 뒤집히는 비율은 첫 재측정 파도(2026-11 말)에서야
     * 보인다. 그 값이 0 에 가까우면 더 늘리거나 "확정 미운행" 상태를 따로 둘 수 있다 — 근거가 생기기
     * 전에 만들지 않는다.
     */
    private static final int REMEASURE_DAYS = 90;

    private static final Period REMEASURE_AFTER = Period.ofDays(REMEASURE_DAYS);

    /** 여행도 배차도 한국 기준이다 — 서버 기본 시간대에 기대지 않는다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final TransitDurationService transitDurationService;
    private final RunningBatches runningBatches;
    private final TransitLegClient transitLegClient;
    private final BatchRunRepository batchRunRepository;

    /** 매시 17분 — 정각에 몰린 다른 배치와 겹치지 않게 어긋냈다. */
    @Scheduled(cron = "0 17 * * * *", zone = "Asia/Seoul")
    public void scheduled() {
        // **수동 실행과 같은 선점을 지난다**(#540). 예전에는 여기서 아래를 곧장 불러,
        // 스케줄러가 도는 중에 관리자가 누르면 둘이 함께 돌았다.
        runNow();
    }

    public void measurePending() {
        LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
        // 손으로 돌리든 스케줄로 돌든 **여기서 실행을 남긴다**(#539 리뷰). 안 남기면 관리자 화면의
        // 마지막 실행 시각이 영영 비어, 정작 "왜 안 돌지" 를 물어야 할 때 답할 것이 없다.
        batchRunRepository.markStarted(BATCH_NAME, now);
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

    @Override
    public String batchName() {
        return BATCH_NAME;
    }

    /** 손으로 돌린다(#537) — 배치 자신의 가드는 그대로 탄다. */
    @Override
    public boolean runNow() {
        // 스케줄러도 이 메서드를 지난다 — 두 경로가 같은 표식을 잡아야 겹치지 않는다(#540).
        // **맥락도 여기서 심는다**(#594). 스케줄러와 수동 실행이 같은 이 자리를 지나므로, 여기 한 번이면
        // 양쪽 다 이름이 붙는다 — measurePending 에 심으면 나중에 다른 진입점이 생겼을 때 또 빠진다.
        return runningBatches.runExclusively(BATCH_NAME, () -> CallerContext.run(CALLER, this::measurePending));
    }
}
