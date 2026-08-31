package com.offway.core.transport.service;

import com.offway.core.transport.domain.MeasuredLeg;
import com.offway.core.transport.domain.TransitLegDuration;
import com.offway.core.transport.domain.TransitLegResult;
import com.offway.core.transport.infrastructure.tago.TransitLegClient;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * <p>한 번에 {@value #MAX_LEGS_PER_RUN} 구간, 구간마다 최대 {@value #MAX_DATE_TRIES} 일을 시도하므로
 * 회당 상한이 {@code 60} 건이다. 시간당 한 번이면 하루 최대 1,440 건 — TAGO 한도 10,000 의 15% 다.
 *
 * <h2>왜 하루만 보지 않는가</h2>
 *
 * <p>주 3회만 뜨는 항로가 있다. 오늘 하루만 물어 비면 "이 구간은 안 다닌다" 로 굳어 버리는데, 그건 틀린
 * 결론이면서 되돌릴 계기도 없다. 조회창 안에서 며칠을 밀어 보고, 그래도 없을 때만 미운행으로 적는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransitDurationRefreshService {

    /** 회당 측정 구간 수 — 한도 보호. */
    private static final int MAX_LEGS_PER_RUN = 20;

    /**
     * 구간 하나에 시도할 날짜 수(오늘부터). 버스 조회창이 오늘~+2일이라 셋이 상한이다 — 넷째 날은
     * 어차피 0건이라 한도만 태운다.
     */
    private static final int MAX_DATE_TRIES = 3;

    /** 여행도 배차도 한국 기준이다 — 서버 기본 시간대에 기대지 않는다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final TransitDurationService transitDurationService;
    private final TransitLegClient transitLegClient;

    /** 매시 17분 — 정각에 몰린 다른 배치와 겹치지 않게 어긋냈다. */
    @Scheduled(cron = "0 17 * * * *", zone = "Asia/Seoul")
    public void measurePending() {
        List<TransitLegDuration> pending = transitDurationService.unmeasured(MAX_LEGS_PER_RUN);
        if (pending.isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now(SERVICE_ZONE);
        int measured = 0;
        int noService = 0;
        int unavailable = 0;
        for (TransitLegDuration leg : pending) {
            switch (measure(leg, today)) {
                case TransitLegResult.Measured(MeasuredLeg found) -> {
                    record(leg, found);
                    measured++;
                }
                case TransitLegResult.NoService ignored -> {
                    // 이것도 결과다 — 적어야 같은 구간을 영원히 다시 재지 않는다.
                    record(leg, null);
                    noService++;
                }
                case TransitLegResult.Unavailable ignored -> unavailable++; // 적지 않는다. 다음 배치가 다시 잰다
            }
        }
        // 0건이어도 남긴다 — 배치가 돌았는지, 왜 0건인지 답할 수 있어야 한다(#310).
        // 셋을 갈라서 남기는 이유: 확보 0건일 때 "다 미운행" 인지 "다 조회 실패" 인지가 대응을 가른다.
        log.info("구간 소요시간 측정 — 대상 {}건: 확보 {} · 미운행 {} · 조회불가 {}",
                pending.size(), measured, noService, unavailable);
    }

    private void record(TransitLegDuration leg, MeasuredLeg found) {
        transitDurationService.record(
                leg.getMode(), leg.getDepCode(), leg.getArrCode(), found, LocalDateTime.now(SERVICE_ZONE));
    }

    /**
     * 조회창 안에서 날짜를 밀어 가며 첫 결과를 찾는다.
     *
     * <p>순차다. 구간 하나에 최대 세 번인데다 앞이 성공하면 뒤를 안 부르므로 병렬로 얻을 것이 없다 —
     * 오히려 실패 구간에서 쓸데없는 호출을 두 번 더 하게 된다.
     *
     * <p><b>조회 불가를 만나면 즉시 멈춘다.</b> 키가 없거나 한도가 말랐는데 날짜만 밀어 세 번 더 부르면
     * 같은 이유로 세 번 다 실패한다 — 한도만 태우고 결론은 같다. 그리고 그 뒤에 "미운행" 이라 적으면
     * 멀쩡한 구간이 영원히 굳는다.
     */
    private TransitLegResult measure(TransitLegDuration leg, LocalDate today) {
        for (int dayOffset = 0; dayOffset < MAX_DATE_TRIES; dayOffset++) {
            TransitLegResult result = transitLegClient.measure(
                    leg.getMode(), leg.getDepCode(), leg.getArrCode(), today.plusDays(dayOffset));
            if (!(result instanceof TransitLegResult.NoService)) {
                return result;
            }
        }
        // 조회창 안 모든 날짜가 정상 응답 + 편 없음 — 그 구간은 다니지 않는다.
        log.debug("{} 미운행으로 기록 {}→{}", leg.getMode().label(), leg.getDepCode(), leg.getArrCode());
        return new TransitLegResult.NoService();
    }
}
