package com.offway.core.trip.service;

import com.offway.core.common.batch.domain.ManualBatch;
import com.offway.core.common.batch.repository.BatchRunRepository;
import com.offway.core.common.external.Caller;
import com.offway.core.common.external.CallerContext;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiBatchPolicy;
import com.offway.core.common.logging.RootCause;
import com.offway.core.region.domain.Region;
import com.offway.core.region.service.RegionQuery;
import com.offway.core.trip.domain.PetFriendlyPlace;
import com.offway.core.trip.infrastructure.pet.PetTourClient;
import com.offway.core.trip.infrastructure.pet.dto.PetTourDetail;
import com.offway.core.trip.infrastructure.pet.dto.PetTourPlace;
import com.offway.core.trip.infrastructure.pet.dto.PetTourResult;
import com.offway.core.trip.repository.PetFriendlyPlaceRepository;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 반려동반 가능 장소를 받아 슬롯 칩의 재료를 채운다(#566).
 *
 * <h2>전국을 한 번에 받아 법정동 코드로 나눈다</h2>
 *
 * <p>이 API 는 <b>요청에만</b> TourAPI 지역코드를 쓴다(#555 함정 3). 지역을 빼고 전국을 받으면 그
 * 제약이 사라지고, 응답의 {@code lDongRegnCd}+{@code lDongSignguCd} 를 우리 {@code legalCode} 와 직접
 * 맞출 수 있다 — 목록이 89콜에서 <b>1콜</b>로 줄고 매칭도 정확해진다(89곳 170 → 442건).
 *
 * <p><b>주소 매칭을 쓰지 않는다.</b> 고캠핑(#510)은 {@code RegionNameMatcher} 로 주소를 읽는데, 그건
 * 그쪽 원본에 코드가 없어서다. 코드가 있으면 그게 낫다 — 같은 이름이 둘인 곳(강원 고성 · 경남 고성)을
 * 가리려고 주소 전체를 파싱할 이유가 없다(#502). 우리 89곳의 {@code legalCode} 는 전부 유일함을 확인했다.
 *
 * <h2>상세는 장소마다다 — 동시성 상한을 둔 병렬</h2>
 *
 * <p>동반 조건은 {@code detailPetTour2} 로 한 건씩 받아야 한다. 442건을 순차로 돌면 지연이 442배가
 * 되므로 {@value #DETAIL_CONCURRENCY} 갈래로 나눈다. 그 이상 벌리지 않는 이유는 상한이 곧 외부에 거는
 * 동시 부하이고, 월 1회 배치라 여기서 몇십 초를 아껴 얻는 것이 없기 때문이다.
 *
 * <p><b>상세 하나가 실패해도 그 장소를 버리지 않는다.</b> 조건을 모르는 반려동반 장소로 남으면 칩은
 * 뜨고 상세만 빈다 — 그쪽이 "데려갈 수 있다" 는 사실까지 잃는 것보다 낫다.
 *
 * <h2>온전히 받았을 때만 정리한다</h2>
 *
 * <p>반려동반을 그만둔 장소는 다음 회차 목록에서 빠지므로 upsert 만으로는 옛 행이 남는다. 그대로 두면
 * <b>이제 데려갈 수 없는 곳에 칩을 띄운다</b> — 사용자가 반려동물을 데리고 갔다가 못 들어간다.
 *
 * <p>다만 <b>목록 조회가 실패하면 "이번에 안 온 것 = 사라진 것" 이 성립하지 않는다</b>. 그때 지우면
 * 멀쩡한 표식을 우리가 없앤다. 상세 실패는 정리를 막지 않는다 — 목록이 온전하면 그 장소는 존재한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PetFriendlyPlaceRefreshService implements ManualBatch {

    private static final String SERVICE_ZONE_ID = "Asia/Seoul";
    private static final ZoneId SERVICE_ZONE = ZoneId.of(SERVICE_ZONE_ID);

    /**
     * 매월 9일 새벽 5시 10분.
     *
     * <p>다른 배치와 시각을 벌렸다 — 지역 장소 풀이 매월 1일 04:00, 축제 기간이 화요일 04:20,
     * 야영장이 매월 8일 04:40, 축제 풀이 매월 6일 04:50 이다. 겹치면 새벽에 외부 호출이 몰린다.
     */
    private static final String MONTHLY_AT_DAWN = "0 10 5 9 * *";

    /**
     * 부팅 뒤 확인 — 배포가 잦아 cron 을 놓칠 수 있다.
     *
     * <p>{@code fixedDelay} 는 재배포하면 주기가 처음부터 다시 센다(#226·#231). 아래 선점이 그것을 막는다.
     */
    private static final String BOOT_CHECK_DELAY = "PT420S";

    private static final String BOOT_CHECK_INTERVAL = "P7D";

    /** 이 주기 안에 이미 돌았으면 건너뛴다 — 재배포가 443콜을 다시 태우지 않게. */
    private static final Duration RUN_INTERVAL = Duration.ofDays(25);

    private static final String BATCH_NAME = "pet-friendly-place-refresh";

    private static final Caller CALLER = Caller.of("반려동반풀배치");

    /**
     * 조회 전체의 시간 상한.
     *
     * <p>목록 2.8초 + 상세 442건을 네 갈래로 도는 시간이다. 단건 실측을 0.5초로 보면 약 55초이고,
     * 외부가 느려질 여유를 얹어 넉넉히 잡는다. 월 1회 배치라 길게 두는 비용이 없다.
     */
    private static final Duration TOTAL_DEADLINE = Duration.ofMinutes(10);

    /** 상세 조회 동시 갈래 수 — 상한이 곧 외부에 거는 동시 부하다. */
    private static final int DETAIL_CONCURRENCY = 4;

    private final PetTourClient petTourClient;
    private final PetFriendlyPlaceRepository petFriendlyPlaceRepository;
    private final RegionQuery regionQuery;
    private final BatchRunRepository batchRunRepository;

    /** 배치를 멈추거나 한도 상한을 거는 스위치(#403). */
    private final ExternalApiBatchPolicy batchPolicy;

    /**
     * 상세 팬아웃 — 데몬으로 둬 종료를 막지 않는다(전부 재시도 가능한 조회다).
     */
    private final ExecutorService detailFanoutExecutor =
            Executors.newFixedThreadPool(DETAIL_CONCURRENCY, runnable -> {
                Thread thread = new Thread(runnable, "pet-tour-detail-fanout");
                thread.setDaemon(true);
                return thread;
            });

    @PreDestroy
    void shutdownDetailFanout() {
        detailFanoutExecutor.shutdownNow();
    }

    @Scheduled(cron = MONTHLY_AT_DAWN, zone = SERVICE_ZONE_ID)
    @Scheduled(initialDelayString = BOOT_CHECK_DELAY, fixedDelayString = BOOT_CHECK_INTERVAL)
    public void refreshIfStale() {
        CallerContext.run(CALLER, () -> {
            if (!batchPolicy.batchMayCall(BATCH_NAME, ExternalApi.PET_TOUR)) {
                // 조용히 넘기지 않는다 — 꺼 둔 줄 모르면 "반려동반 칩이 왜 안 뜨지" 가 된다.
                log.info("반려동반 풀 배치가 꺼져 있거나 배치 한도를 넘겨 건너뜁니다");
                return;
            }
            LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
            // **선점으로 판정과 기록을 한 문장에 묶는다.** hasRunSince + markStarted 는 그 사이에 다른
            // 실행이 끼어들 수 있고, 이 배치는 트리거가 셋이다(cron · 부팅 확인 · 관리자 수동). 둘이
            // 겹치면 443콜을 두 번 쏜다 — 일일 한도가 거의 찬다.
            //
            // 선점이 곧 기록이라 **실패한 회차도 다음 간격까지 건너뛴다**. 회차가 비싸므로 의도한 것이다.
            if (!batchRunRepository.tryStartSince(BATCH_NAME, now.minus(RUN_INTERVAL), now)) {
                log.info("반려동반 풀을 최근 {}일 안에 이미 받아 갱신을 건너뜁니다", RUN_INTERVAL.toDays());
                return;
            }
            refresh();
        });
    }

    /**
     * 한 회차의 결과.
     *
     * @param saved 저장한 건수
     * @param detailsMissing 상세를 못 받은 건수 — 칩은 뜨지만 열 내용이 없다
     * @param complete 목록 조회가 온전했나 — 거짓이면 사라진 것 정리를 건너뛴다
     */
    public record RefreshOutcome(int saved, int detailsMissing, boolean complete) {

        private static final RefreshOutcome NOTHING = new RefreshOutcome(0, 0, false);

        /** 이번 회차는 없던 일이다. */
        static RefreshOutcome nothing() {
            return NOTHING;
        }
    }

    /** 전국 반려동반 장소를 받아 우리 89곳 것만 저장한다. */
    public RefreshOutcome refresh() {
        // **초 단위로 자른다.** fetched_at 이 DATETIME(소수점 없음)이라 나노초가 붙은 값을 넣으면
        // MySQL 이 반올림하거나 버린다. 그 결과가 저장값보다 커지는 순간 아래 정리가 **방금 넣은
        // 장소를 지운다**(#510 에서 같은 함정을 막았다).
        return refresh(LocalDateTime.now(SERVICE_ZONE).truncatedTo(ChronoUnit.SECONDS));
    }

    /**
     * 회차 시각을 지정해 받는다.
     *
     * <p><b>시각이 인자인 이유</b>는 그것이 곧 "이번 회차" 의 표식이기 때문이다. 이 값으로 저장하고
     * 이 값보다 오래된 행을 지우므로, 테스트가 시계에 기대지 않으려면 열려 있어야 한다.
     */
    public RefreshOutcome refresh(LocalDateTime fetchedAt) {
        Map<String, Long> regionIdByLegalCode = regionIdByLegalCode();
        if (regionIdByLegalCode.isEmpty()) {
            log.info("반려동반 풀 — 지역 마스터가 비어 있어 건너뜁니다");
            return RefreshOutcome.nothing();
        }

        long startedAt = System.nanoTime();
        PetTourResult received;
        try {
            received = petTourClient.findAll(TOTAL_DEADLINE);
        } catch (RuntimeException e) {
            // 조회가 깨지면 이번 회차는 없던 일이다. 기존 값을 덮지 않으므로 화면은 그대로다.
            log.warn("반려동반 풀 조회 실패 — 이번 회차를 건너뜁니다 cause={}", RootCause.label(e));
            return RefreshOutcome.nothing();
        }
        if (received.isEmpty()) {
            // 키가 없거나(로컬) 결과가 비었다. 빈 결과로 정리를 돌리면 있는 표식을 전부 지운다.
            log.info("반려동반 풀 — 받은 장소가 없어 이번 회차를 건너뜁니다 전체={}", received.totalCount());
            return RefreshOutcome.nothing();
        }

        List<Ours> ours = ourRegionsOnly(received.places(), regionIdByLegalCode);
        if (ours.isEmpty()) {
            // 전국 9,679건 중 우리 89곳이 0건이면 코드 매칭이 깨진 것이다 — 정리를 돌리면 안 된다.
            log.warn("반려동반 풀 — 전국 {}건을 받았지만 우리 89곳에 붙은 것이 없습니다. 법정동 코드 매칭을 확인하세요",
                    received.places().size());
            return RefreshOutcome.nothing();
        }

        return saveWithDetails(ours, fetchedAt, remainingOf(startedAt));
    }

    /** 법정 시군구코드 → 지역 id. 89곳의 코드는 전부 유일함을 확인했다(2026-09-13 운영 DB). */
    private Map<String, Long> regionIdByLegalCode() {
        Map<String, Long> byCode = new HashMap<>();
        for (Region region : regionQuery.all()) {
            if (region.getLegalCode() != null) {
                byCode.put(region.getLegalCode(), region.getId());
            }
        }
        return byCode;
    }

    /**
     * 우리 89곳에 속한 것만 남긴다.
     *
     * <p>전국 9,679건 중 대부분이 89곳 밖이라 걸러지는 것 자체는 정상이다. 0건일 때만 신호다(위에서 판정).
     */
    private static List<Ours> ourRegionsOnly(List<PetTourPlace> places, Map<String, Long> regionIdByLegalCode) {
        List<Ours> ours = new ArrayList<>();
        for (PetTourPlace place : places) {
            Long regionId = regionIdByLegalCode.get(place.legalCode());
            if (regionId != null) {
                ours.add(new Ours(place, regionId));
            }
        }
        return ours;
    }

    /**
     * 상세를 병렬로 받아 저장하고, 이번에 안 온 것을 지운다.
     *
     * <p><b>남은 예산을 호출 하나까지 내려보낸다.</b> 전체 상한만 두고 각 호출을 자기 timeout 대로
     * 두면, 마감 직전에 시작한 호출이 그만큼 더 대기해 전체 상한을 넘긴다(CLAUDE.md 성능 규약).
     */
    private RefreshOutcome saveWithDetails(List<Ours> ours, LocalDateTime fetchedAt, Duration budget) {
        long fanoutStartedAt = System.nanoTime();
        ConcurrentLinkedQueue<PetFriendlyPlace> collected = new ConcurrentLinkedQueue<>();
        AtomicInteger missing = new AtomicInteger();

        List<CompletableFuture<Void>> futures = new ArrayList<>(ours.size());
        for (Ours our : ours) {
            // 맥락을 붙여 넘긴다(#285·#421). 스레드가 바뀌면 호출 주체와 사용량 집계가 미상이 된다.
            futures.add(CompletableFuture.runAsync(
                    CallerContext.wrap(() -> {
                        Duration left = budget.minus(elapsedSince(fanoutStartedAt));
                        PetTourDetail detail = left.isNegative() || left.isZero()
                                ? missed(our, missing)
                                : petTourClient.findDetail(our.place().contentId(), left)
                                        .orElseGet(() -> missed(our, missing));
                        collected.add(detail.toPlace(our.regionId(), our.place().title(), fetchedAt));
                    }),
                    detailFanoutExecutor));
        }

        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .get(budget.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("반려동반 상세 팬아웃이 중단됐습니다 — 이번 회차를 건너뜁니다");
            return RefreshOutcome.nothing();
        } catch (Exception e) {
            // 전체 상한에 걸렸거나 예상 밖 실패다. **목록은 온전하므로 받은 것까지는 저장한다** —
            // 상세가 빈 장소는 칩만 뜨고, 그쪽이 표식을 통째로 잃는 것보다 낫다.
            log.warn("반려동반 상세 팬아웃이 상한에 걸렸습니다 받은={}건 cause={}",
                    collected.size(), RootCause.label(e));
        }

        List<PetFriendlyPlace> places = List.copyOf(collected);
        if (places.isEmpty()) {
            log.warn("반려동반 풀 — 상세를 하나도 조립하지 못해 이번 회차를 건너뜁니다");
            return RefreshOutcome.nothing();
        }

        int saved = petFriendlyPlaceRepository.upsertAll(places);
        // 목록이 온전했으므로 이번에 안 온 것은 반려동반을 그만둔 장소다.
        int removed = petFriendlyPlaceRepository.deleteFetchedBefore(fetchedAt);
        log.info("반려동반 풀 갱신 저장={}건 상세없음={}건 정리={}건 지역={}곳",
                saved, missing.get(), removed, distinctRegions(places));
        return new RefreshOutcome(saved, missing.get(), true);
    }

    /** 상세를 못 받았다 — 칩은 뜨고 열 내용만 빈다. 세어서 로그로 드러낸다. */
    private static PetTourDetail missed(Ours our, AtomicInteger missing) {
        missing.incrementAndGet();
        return PetTourDetail.unknown(our.place().contentId());
    }

    private static long distinctRegions(List<PetFriendlyPlace> places) {
        return places.stream().map(PetFriendlyPlace::getRegionId).distinct().count();
    }

    private static Duration remainingOf(long startedAt) {
        Duration left = TOTAL_DEADLINE.minus(elapsedSince(startedAt));
        return left.isNegative() ? Duration.ZERO : left;
    }

    private static Duration elapsedSince(long startedAtNanos) {
        return Duration.ofNanos(System.nanoTime() - startedAtNanos);
    }

    @Override
    public String batchName() {
        return BATCH_NAME;
    }

    /** 손으로 돌린다(#537) — 배치 자신의 가드는 그대로 탄다. */
    @Override
    public void runNow() {
        refreshIfStale();
    }

    /** 목록에서 온 장소와 그것이 붙은 우리 지역. */
    private record Ours(PetTourPlace place, Long regionId) {
    }
}
