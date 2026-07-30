package com.offway.core.trip.service;

import com.offway.core.common.cache.ExternalDataCache;
import com.offway.core.common.cache.ExternalDataCache.Loaded;
import com.offway.core.region.domain.Region;
import com.offway.core.transport.domain.Coordinate;
import com.offway.core.trip.domain.RegionContent;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
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
    private final ExternalDataCache<Long, RegionContent> cache = new ExternalDataCache<>(MAX_CACHED_REGIONS);

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
    public Map<Long, RegionContent> contentForAll(
            List<Region> targets, List<Region> neighborPool, Duration deadline) {
        if (targets.isEmpty()) {
            return Map.of();
        }
        Instant deadlineAt = Instant.now().plus(deadline);
        Map<Long, RegionContent> contents = new ConcurrentHashMap<>();

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        int rejected = 0;
        for (Region region : targets) {
            try {
                futures.add(CompletableFuture.runAsync(fillTask(region, neighborPool, deadlineAt, contents),
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

        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .get(deadline.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("지역 콘텐츠 팬아웃 시간 상한({}) 초과 — {}/{}건만 채웁니다",
                    deadline, contents.size(), targets.size());
        } catch (ExecutionException e) {
            log.warn("지역 콘텐츠 팬아웃이 예외로 끝났습니다 — {}/{}건만 채웁니다", contents.size(), targets.size(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("지역 콘텐츠 팬아웃이 중단됐습니다 — {}/{}건만 채웁니다", contents.size(), targets.size());
        }
        return contents;
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
            Region region, List<Region> neighborPool, Instant deadlineAt, Map<Long, RegionContent> contents) {
        return () -> {
            if (!Instant.now().isBefore(deadlineAt)) {
                return;
            }
            contents.put(region.getId(), contentFor(region, neighborPool));
        };
    }

    /**
     * 한 지역의 콘텐츠. 볼거리가 충분하면 그대로, 부족하면 인접 50km 지역(가까운 순, 최대 {@value #MAX_NEIGHBORS}곳)을 충분해질
     * 때까지 병합한다. {@code neighborPool} 은 인접 후보(보통 전체 지역) — 자기 자신은 자동 제외한다.
     */
    RegionContent contentFor(Region region, List<Region> neighborPool) {
        RegionContent content = fetch(region);
        if (content.isSufficient()) {
            return content;
        }
        for (Region neighbor : nearestNeighbors(region, neighborPool)) {
            content = content.expandedWith(fetch(neighbor));
            if (content.isSufficient()) {
                break;
            }
        }
        if (content.neighborIncluded()) {
            log.info("콘텐츠 확장 region={} → contentCount={} categories={}",
                    region.getId(), content.contentCount(), content.categories().size());
        }
        return content;
    }

    /** 캐시 무효화 — 운영상 강제 갱신, 그리고 공유 컨텍스트 통합 테스트 격리용(캐시가 이전 시나리오를 물지 않게). */
    public void evictCache() {
        cache.evictAll();
    }

    /**
     * 지역 콘텐츠 조회 — 캐시(single-flight) 우선. 실패하면 콘텐츠는 부가 정보라 예외를 올리지 않고 마지막 성공값(있으면), 없으면
     * 빈 콘텐츠로 degrade 하고 짧게 캐시해 실패 동안 요청이 몰리지 않게 한다.
     */
    private RegionContent fetch(Region region) {
        return cache.get(region.getId(), (id, stale) -> {
            try {
                RegionContent fresh = tourApiClient
                        .findByArea(region.getAreaCode(), region.getSigunguCode(), null, SAMPLE_ROWS)
                        .toRegionContent();
                return new Loaded<>(fresh, CACHE_TTL);
            } catch (TourApiException e) {
                RegionContent fallback = stale != null ? stale : RegionContent.EMPTY;
                log.warn("지역 콘텐츠 조회 실패 — {} 로 degrade region={}",
                        stale != null ? "마지막 성공값" : "빈 콘텐츠", id, e);
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
