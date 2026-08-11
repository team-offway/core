package com.offway.core.trip.service;

import com.offway.core.common.cache.ExternalDataCache;
import com.offway.core.common.logging.DegradeTally;
import com.offway.core.common.logging.RootCause;
import com.offway.core.common.cache.ExternalDataCache.Loaded;
import com.offway.core.region.domain.Region;
import com.offway.core.transport.domain.Coordinate;
import com.offway.core.trip.domain.RegionContent;
import com.offway.core.trip.domain.StoredRegionContent;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.repository.RegionContentRepository;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 지역 콘텐츠(볼거리 수·대표 이미지·categories) 조회 — TourAPI 로 지역별 콘텐츠를 얻고, 볼거리가 부족하면 인접 50km 지역 콘텐츠로
 * 확장한다(F3). TourAPI 는 read-timeout 이 길어 <b>트랜잭션 밖</b>에서 호출한다(persistence-convention).
 *
 * <p>콘텐츠는 89개 고정 지역의 느리게 변하는 값이라 지역별로 인메모리 캐시한다. 조회가 실패하면(TourAPI 타임아웃 등) 콘텐츠는 화면의
 * <b>부가 정보</b>라 예외를 올리지 않고 빈 콘텐츠(이미지·categories 없음)로 degrade 하며, 마지막 성공값이 있으면 재사용한다
 * (stale-while-error). 그래서 홈·추천이 콘텐츠 실패로 502 가 되지 않고, 실패가 이어져도 매 요청이 6초씩 재시도하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegionContentProvider {

    /** 콘텐츠 표본 크기 — 대표 이미지·categories 산출용. 볼거리 수는 표본과 무관하게 totalCount 로 온다. */
    private static final int SAMPLE_ROWS = 30;
    /** 콘텐츠 부족 시 묶을 인접 반경(㎞) — feature-spec F3. */
    private static final double NEIGHBOR_RADIUS_KM = 50.0;
    /** 지역 하나당 병합할 인접 지역 상한(호출량 방어). */
    private static final int MAX_NEIGHBORS = 3;
    /** 성공 캐시 TTL — 지역 콘텐츠는 느리게 변한다. */
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    /** 실패 캐시 TTL — 조회 실패가 이어져도 매 요청이 6초씩 재시도하지 않게 짧게 폴백값을 눌러둔다. */
    private static final Duration FAILURE_CACHE_TTL = Duration.ofMinutes(5);

    /** 보관할 지역 수. 키가 지역 id 라 <b>키 공간이 유한</b>하다 — 인구감소지역 89곳이 전부고, 고시 개정 여유를 얹었다. */
    private static final int MAX_CACHED_REGIONS = 128;

    /**
     * 팬아웃 동시성 상한. 외부가 느려도 후보를 순차로 기다리지 않게 병렬로 돌리되, 상한을 둬 TourAPI 부하·쿼터 소모를
     * 억제한다.
     *
     * <p><b>요청 경로와 워밍 경로가 같은 값을 쓰는 게 아니라 같은 메서드를 쓴다</b>({@link #contentForAll}) — 두 경로의
     * 동시성이 갈릴 여지를 코드에서 없앴다(성능 규약 "요청 경로와 워밍 경로의 동시성을 다르게 두지 않는다").
     */
    private static final int FANOUT_CONCURRENCY = 12;

    /**
     * <b>요청 경로</b>의 팬아웃 시간 상한. 호출 하나의 timeout(6초)과는 별개다 — 후보 20곳 × (자기 + 인접 최대
     * {@value #MAX_NEIGHBORS}) 을 동시성 {@value #FANOUT_CONCURRENCY} 로 돌려도 최악은 수십 초다.
     *
     * <p>상한에 걸리면 <b>그때까지 채워진 것만</b> 쓴다. 콘텐츠는 화면의 부가 정보라 일부가 비는 것이 이미 정상
     * 동작이고(조회 실패 시 빈 콘텐츠로 degrade), 랭킹과 달리 부분 데이터가 순위를 틀리게 만들지 않는다.
     */
    public static final Duration REQUEST_FANOUT_DEADLINE = Duration.ofSeconds(15);

    /**
     * <b>백그라운드 워밍</b>의 팬아웃 시간 상한. 요청 경로보다 훨씬 길다 — 89곳을 끝까지 채워야 첫 요청이 즉답이 되는데,
     * 사용자를 기다리게 하는 쪽이 아니라서 서두를 이유가 없다.
     *
     * <p><b>동시성은 공유하고 시간 예산만 나눈다.</b> 동시성은 외부에 거는 부하라 경로가 갈리면 상한이 무의미해지지만,
     * 시간 예산은 "누가 기다리는가" 의 문제라 요청과 배치가 같을 이유가 없다.
     */
    public static final Duration WARMING_FANOUT_DEADLINE = Duration.ofMinutes(5);

    private final TourApiClient tourApiClient;
    private final RegionContentRepository regionContentRepository;
    /**
     * 빈 지역에 동시 요청이 몰렸을 때 첫 적재를 기다릴 상한. loader 가 TourAPI <b>단일 호출</b>(timeout 6초)이라
     * 여유 1초를 얹었다.
     *
     * <p>여기서 기다리지 않으면 폴백이 {@link RegionContent#EMPTY} 라 <b>조용히 빈 화면</b>이 된다 — 502 처럼
     * 눈에 띄지도 않아 아무도 모른다.
     */
    private static final Duration FIRST_LOAD_WAIT = Duration.ofSeconds(7);

    private final ExternalDataCache<Long, RegionContent> cache = new ExternalDataCache<>(MAX_CACHED_REGIONS, FIRST_LOAD_WAIT);

    /**
     * 대기 큐 상한. {@code Executors.newFixedThreadPool} 은 <b>무제한 큐</b>라, 외부가 느려지면 요청·워밍 작업이
     * 한없이 쌓여 서로를 밀어낸다. 워밍 한 배치(89곳)와 동시 요청 몇 건을 담을 만큼만 두고 넘치면 거절한다.
     */
    private static final int FANOUT_QUEUE_CAPACITY = 256;

    /**
     * 팬아웃 전용 풀. 요청마다 만들지 않고 빈이 소유한다 — 워밍은 5시간에 한 번이지만 요청은 매번이라, 풀 생성 비용을
     * 요청 경로에 얹을 이유가 없다. 데몬 스레드로 둬 종료를 막지 않는다(전부 재시도 가능한 조회다).
     *
     * <p>거절 정책은 기본(`AbortPolicy`)이다 — 호출부가 {@link java.util.concurrent.RejectedExecutionException} 을
     * 받아 그 지역만 건너뛴다. `CallerRunsPolicy` 로 두면 넘칠 때 <b>요청 스레드가 외부 호출을 직접 떠안아</b>
     * 상한이 무의미해진다.
     */
    private final ExecutorService fanoutExecutor = new ThreadPoolExecutor(
            FANOUT_CONCURRENCY,
            FANOUT_CONCURRENCY,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(FANOUT_QUEUE_CAPACITY),
            runnable -> {
                Thread thread = new Thread(runnable, "region-content-fanout");
                thread.setDaemon(true);
                return thread;
            });

    @PreDestroy
    void shutdownFanout() {
        fanoutExecutor.shutdownNow();
    }

    /**
     * <b>저장된</b> 지역 콘텐츠를 읽는다 — 요청 경로가 쓰는 길이다(#193).
     *
     * <p>외부를 부르지 않는다. 예전에는 요청마다 89곳 팬아웃과 인접 병합을 돌렸는데, 캐시가 비면(배포 직후)
     * 그 비용을 사용자가 그대로 떠안았다. 적재는 {@link RegionContentRefreshService} 가 하루 한 번 한다.
     *
     * <p>아직 적재 전이면 <b>키가 없다</b>. 콘텐츠는 화면의 부가 정보라 호출자가 빈 콘텐츠로 취급하면 되고,
     * 그 자리에서 89곳을 긁어 사용자를 수십 초 기다리게 하지 않는다.
     *
     * @return 지역ID → 콘텐츠(인접 병합까지 끝난 값)
     */
    public Map<Long, RegionContent> storedForAll(List<Long> regionIds) {
        if (regionIds.isEmpty()) {
            return Map.of();
        }
        return regionContentRepository.findByRegionIds(regionIds).stream()
                .collect(Collectors.toMap(StoredRegionContent::getRegionId, StoredRegionContent::toRegionContent));
    }

    /**
     * 여러 지역의 콘텐츠를 <b>동시성 상한을 둔 병렬</b>로 채운다. 홈·추천·워밍이 전부 이 메서드를 쓴다.
     *
     * <p>순차로 돌면 지연이 후보 수만큼 곱해진다 — TourAPI 는 지역당 수 초에 간헐 타임아웃이라, 20곳이면 그게 그대로
     * 응답시간이 된다(ADR 0001 의 cold median 10초).
     *
     * <p>지역 하나의 실패는 그 지역만 빈 콘텐츠가 되고 나머지를 막지 않는다. 전체가 {@code deadline} 을 넘기면
     * 그때까지 채워진 것만 돌려준다.
     *
     * @param targets 콘텐츠를 채울 지역
     * @param neighborPool 인접 후보(보통 전체 지역)
     * @param deadline 팬아웃 전체의 시간 상한 — {@link #REQUEST_FANOUT_DEADLINE} 또는
     *     {@link #WARMING_FANOUT_DEADLINE}
     * @return 지역ID → 콘텐츠. 채우지 못한 지역은 <b>키가 없다</b>(호출자가 빈 콘텐츠로 취급한다)
     */
    public RegionContents contentForAll(
            List<Region> targets, List<Region> neighborPool, Duration deadline) {
        if (targets.isEmpty()) {
            return RegionContents.empty();
        }
        // 이 실행에서만 센다. 빈에 두면 요청 경로의 실패가 워밍 집계에 섞인다.
        DegradeTally degraded = new DegradeTally();
        // 벽시계(Instant) 가 아니라 단조 시계로 잰다 — 시스템 시각이 보정되면 예산이 늘거나 즉시 만료된다.
        long deadlineNanos = System.nanoTime() + deadline.toNanos();
        Map<Long, RegionContent> contents = new ConcurrentHashMap<>();

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        int rejected = 0;
        int index = 0;
        for (; index < targets.size(); index++) {
            if (isPast(deadlineNanos)) {
                break; // 제출 도중 예산이 끝났다 — 더 넣어봐야 아래 fillTask 가 즉시 버린다
            }
            Region region = targets.get(index);
            try {
                futures.add(CompletableFuture.runAsync(fillTask(region, neighborPool, deadlineNanos, contents, degraded),
                                fanoutExecutor)
                        .exceptionally(error -> {
                            // contentFor 는 스스로 degrade 하는 게 계약이지만, 그 계약이 깨져도 나머지를 막지 않는다.
                            log.warn("지역 콘텐츠 팬아웃 실패(계속) region={}", region.getId(), error);
                            return null;
                        }));
            } catch (RejectedExecutionException e) {
                rejected++; // 큐가 찼다 — 이 지역은 빈 콘텐츠로 두고 나머지를 계속한다
            }
        }
        if (rejected > 0) {
            log.warn("지역 콘텐츠 팬아웃 큐 포화({}) — {}건을 건너뜁니다", FANOUT_QUEUE_CAPACITY, rejected);
        }
        if (index < targets.size()) {
            log.warn("지역 콘텐츠 팬아웃 시간 상한({}) 초과 — {}건은 제출하지 못했습니다",
                    deadline, targets.size() - index);
        }

        try {
            // 전체 예산이 아니라 <b>남은</b> 예산만 기다린다. 제출에 쓴 시간을 빼지 않으면 상한이 그만큼 늘어난다.
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .get(Math.max(0, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            log.warn("지역 콘텐츠 팬아웃 시간 상한({}) 초과 — {}/{}건만 채웁니다",
                    deadline, contents.size(), targets.size());
        } catch (ExecutionException e) {
            log.warn("지역 콘텐츠 팬아웃이 예외로 끝났습니다 — {}/{}건만 채웁니다", contents.size(), targets.size(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("지역 콘텐츠 팬아웃이 중단됐습니다 — {}/{}건만 채웁니다", contents.size(), targets.size());
        }
        return new RegionContents(contents, degraded.total(), degraded.summaryFragment());
    }

    /**
     * 한 지역을 채우는 작업. <b>큐에서 대기하는 동안 예산이 끝났으면 외부를 부르지 않고 즉시 끝낸다.</b>
     *
     * <p>{@code allOf(...).get(deadline)} 은 <b>기다림만</b> 끊는다 — 이미 제출된 작업은 그대로 남아 각자
     * read-timeout 까지 스레드를 물고, 뒤이은 요청·워밍이 그 뒤에 쌓인다. 시작 시점에 한 번 보는 것만으로
     * 대기 중이던 작업이 즉시 빠져나가 큐가 풀린다.
     *
     * <p>이미 <b>실행 중</b>인 호출은 여기서 못 끊는다(최대 {@value #FANOUT_CONCURRENCY}건 × 클라이언트 timeout).
     * 그걸 끊으려면 남은 예산을 {@code TourApiClient} 까지 내려야 하는데, 그 포트는 소비자가 넷이라 별도 작업이다.
     */
    private Runnable fillTask(
            Region region,
            List<Region> neighborPool,
            long deadlineNanos,
            Map<Long, RegionContent> contents,
            DegradeTally degraded) {
        return () -> {
            if (isPast(deadlineNanos)) {
                return;
            }
            contents.put(region.getId(), contentFor(region, neighborPool, degraded));
        };
    }

    /** 단조 시계 기준 마감 경과 여부. 뺄셈으로 비교해 {@code nanoTime} 랩어라운드에도 안전하다. */
    private static boolean isPast(long deadlineNanos) {
        return System.nanoTime() - deadlineNanos >= 0;
    }

    /**
     * 한 지역의 콘텐츠. 볼거리가 충분하면 그대로, 부족하면 인접 50km 지역(가까운 순, 최대 {@value #MAX_NEIGHBORS}곳)을 충분해질
     * 때까지 병합한다. {@code neighborPool} 은 인접 후보(보통 전체 지역) — 자기 자신은 자동 제외한다.
     */
    RegionContent contentFor(Region region, List<Region> neighborPool, DegradeTally degraded) {
        RegionContent content = fetch(region, degraded);
        if (content.isSufficient()) {
            return content;
        }
        for (Region neighbor : nearestNeighbors(region, neighborPool)) {
            content = content.expandedWith(fetch(neighbor, degraded));
            if (content.isSufficient()) {
                break;
            }
        }
        if (content.neighborIncluded()) {
            log.debug("콘텐츠 확장 region={} → contentCount={} categories={}",
                    region.getId(), content.contentCount(), content.categories().size());
        }
        return content;
    }

    /**
     * 팬아웃 한 번의 결과 — 채운 콘텐츠와 그 실행에서 degrade 된 지역 수.
     *
     * <p><b>왜 실행 단위인가.</b> 빈 필드에 누적하면 요청 경로의 실패가 워밍 로그에 섞이고(같은 팬아웃을 둘이
     * 공유한다), 상한을 넘겨 늦게 끝난 작업이 다음 회차 집계에 들어간다. 그러면 "이번 워밍에서 몇 곳이
     * degrade 됐나" 를 로그가 답하지 못한다 — 세어서 남기는 의미가 사라진다.
     *
     * @param byRegionId 지역별 콘텐츠
     * @param degraded 이 실행에서 외부 실패로 degrade 된 지역 수
     * @param degradeSummary 사유별 내역을 로그에 그대로 붙일 조각({@code (429=39)}). degrade 가 없으면 빈 문자열
     */
    public record RegionContents(Map<Long, RegionContent> byRegionId, int degraded, String degradeSummary) {

        /** 채울 대상이 없었던 실행 — degrade 도 없다. */
        static RegionContents empty() {
            return new RegionContents(Map.of(), 0, "");
        }
    }


    /** 캐시 무효화 — 운영상 강제 갱신, 그리고 공유 컨텍스트 통합 테스트 격리용(캐시가 이전 시나리오를 물지 않게). */
    public void evictCache() {
        cache.evictAll();
    }

    /**
     * 지역 콘텐츠 조회 — 캐시(single-flight) 우선. 실패하면 콘텐츠는 부가 정보라 예외를 올리지 않고 마지막 성공값(있으면), 없으면
     * 빈 콘텐츠로 degrade 하고 짧게 캐시해 실패 동안 요청이 몰리지 않게 한다.
     */
    private RegionContent fetch(Region region, DegradeTally degraded) {
        return cache.get(region.getId(), (id, stale) -> {
            try {
                RegionContent fresh = tourApiClient
                        .findByArea(region.getAreaCode(), region.getSigunguCode(), null, SAMPLE_ROWS)
                        .toRegionContent();
                return new Loaded<>(fresh, CACHE_TTL);
            } catch (TourApiException e) {
                RegionContent fallback = stale != null ? stale : RegionContent.EMPTY;
                String source = stale != null ? "마지막 성공값" : "빈 콘텐츠";
                // 스택을 찍지 않는다. 외부 실패는 예상 범위 안의 사건이라 cause 면 충분한데, 예외를 넘기면
                // Reactor 체크포인트까지 붙어 한 건이 60줄이 넘는다 — 89개 지역이면 로그가 수천 줄이 되고
                // 정작 알아야 할 "몇 곳이 degrade 됐나" 가 그 안에 묻힌다(#191).
                //
                // 사유마다 첫 건만 WARN 으로 올린다. 89곳이 같은 이유로 실패하면 나머지 88줄은 같은 말이라
                // 요약 한 줄이 대신한다. 그렇다고 전부 지우면 폴백이 정상처럼 보여 장애를 아무도 모른다.
                if (degraded.add(RootCause.label(e))) {
                    log.warn("지역 콘텐츠 조회 실패 — {} 로 degrade region={} cause={}", source, id, RootCause.of(e));
                } else {
                    log.debug("지역 콘텐츠 조회 실패 — {} 로 degrade region={} cause={}", source, id, RootCause.of(e));
                }
                return new Loaded<>(fallback, FAILURE_CACHE_TTL);
            }
        }, RegionContent.EMPTY);
    }

    /** 반경 50km 안의 다른 지역을 가까운 순으로 상한만큼. */
    private List<Region> nearestNeighbors(Region region, List<Region> pool) {
        Coordinate center = new Coordinate(region.getLat(), region.getLng());
        return pool.stream()
                .filter(candidate -> !candidate.getId().equals(region.getId()))
                .map(candidate -> Map.entry(
                        candidate, center.haversineKmTo(new Coordinate(candidate.getLat(), candidate.getLng()))))
                .filter(entry -> entry.getValue() <= NEIGHBOR_RADIUS_KM)
                .sorted(Comparator.comparingDouble(Map.Entry::getValue))
                .limit(MAX_NEIGHBORS)
                .map(Map.Entry::getKey)
                .toList();
    }
}
