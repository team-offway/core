package com.offway.core.trip.service;

import com.offway.core.region.domain.Region;
import com.offway.core.region.service.RegionMaster;
import com.offway.core.trip.domain.CategoryCounts;
import com.offway.core.trip.domain.RegionContent;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 필터칩별 지역 수를 세어 들고 있는다(#266) — 칩을 노출하는 두 자리({@code GET /api/v1/categories}, 홈의 {@code filters})가
 * 같은 값을 쓴다.
 *
 * <p><b>매 요청 세지 않는다.</b> 세는 입력이 느리게 변한다 — 인구감소지역은 고시로 정해진 89곳이고, 그 콘텐츠는
 * {@link RegionContentRefreshService} 가 <b>주 1회</b> 갈아끼운다. 요청마다 89행을 읽어 다시 세면 안 바뀌는 답을 반복해서 만드는 셈이다.
 *
 * <p><b>키 공간은 하나다.</b> 캐시하는 값이 지역·사용자별로 갈리지 않고 서비스 전체에 하나뿐이라(칩 {@link
 * com.offway.core.trip.domain.Category} 5개의 개수 묶음), 상한·LRU 를 설계할 키가 애초에 없다.
 *
 * <p><b>갱신은 두 갈래다.</b>
 *
 * <ul>
 *   <li><b>적재가 끝나면 바로 버린다</b>({@link #invalidate()}). 원본이 바뀐 그 순간을 아는 유일한 자리라, 여기서 버리면 새 배포의
 *       첫 적재가 곧바로 칩에 반영된다. TTL 만 두면 그 사이 사용자가 "전부 0" 인 칩을 본다.
 *   <li><b>{@value #RECOUNT_INTERVAL_TEXT} 마다 다시 센다</b> — 위 경로를 타지 않은 변경(다른 프로세스의 적재, 시드 마이그레이션
 *       직후 등)까지 덮는 안전망이다. 재계산은 89행 한 번이라 하루 24번을 써도 무시할 만하다.
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegionCategoryCountProvider {

    /** 안전망 재계산 간격 — 위 클래스 주석의 근거. */
    private static final Duration RECOUNT_INTERVAL = Duration.ofHours(1);

    private static final String RECOUNT_INTERVAL_TEXT = "1시간";

    private final RegionMaster regionMaster;
    private final RegionContentProvider regionContentProvider;

    /**
     * 마지막으로 센 결과. <b>{@code null} 이 "아직 세지 않았다"</b> 다 — 결과가 전부 0 인 것과 구별해야 다음 조회가 다시 센다.
     */
    private volatile Snapshot snapshot;

    /** 센 결과와 센 시각(단조 시계). 벽시계로 재면 시스템 시각 보정에 간격이 늘거나 즉시 만료된다. */
    private record Snapshot(CategoryCounts counts, long countedNanos) {

        private boolean isStale() {
            return System.nanoTime() - countedNanos >= RECOUNT_INTERVAL.toNanos();
        }
    }

    /**
     * 기동이 끝난 뒤 한 번 센다 — 첫 요청이 계산을 떠안지 않게. 마이그레이션(Flyway)이 시드를 넣은 뒤여야 해서
     * {@code @PostConstruct} 가 아니라 이 이벤트를 쓴다({@link RegionMaster} 와 같은 이유).
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warm() {
        recount();
    }

    /** 지금 칩별 지역 수. 오래됐거나 아직 안 셌으면 그 자리에서 센다(89행 한 번). */
    public CategoryCounts counts() {
        Snapshot current = snapshot;
        if (current != null && !current.isStale()) {
            return current.counts();
        }
        return recount();
    }

    /**
     * 다음 조회가 다시 세게 한다 — 지역 콘텐츠 적재가 끝나면 부른다.
     *
     * <p>지금 세지 않고 버리기만 한다. 적재는 배치 스레드에서 끝나는데 거기서 다시 세면 아무도 안 볼 수도 있는 값을 만드는 것이고,
     * 어차피 다음 조회가 센다.
     */
    public void invalidate() {
        snapshot = null;
    }

    /**
     * 89곳 전부의 콘텐츠로 다시 센다.
     *
     * <p>이중 계산을 막지 않는다({@link RegionMaster} 와 같은 판단) — 두 스레드가 동시에 들어와도 같은 입력에서 같은 결과를 만들어
     * 같은 값으로 덮을 뿐이고, 락을 걸면 89행 조회 하나 때문에 요청 스레드가 서로를 기다린다.
     */
    private CategoryCounts recount() {
        List<Region> all = regionMaster.all();
        if (all.isEmpty()) {
            // 시드가 아직 없는 상태(초기 부팅)다. 스냅샷을 세우지 않아 다음 조회가 다시 시도한다 —
            // 여기서 "전부 0" 을 굳히면 시드가 들어온 뒤에도 한 시간 동안 빈 칩이 나간다.
            log.warn("지역이 없어 필터칩 개수를 세지 못했습니다 — 다음 조회에서 다시 셉니다");
            return CategoryCounts.EMPTY;
        }
        List<Long> regionIds = all.stream().map(Region::getId).toList();
        Map<Long, RegionContent> stored = regionContentProvider.storedForAll(regionIds);
        // 콘텐츠가 아직 없는 지역도 빈 콘텐츠로 세운다 — 그래야 ALL 이 목록의 전체 건수와 같아진다.
        List<RegionContent> contents = regionIds.stream()
                .map(regionId -> stored.getOrDefault(regionId, RegionContent.EMPTY))
                .toList();
        CategoryCounts counted = CategoryCounts.of(contents);
        snapshot = new Snapshot(counted, System.nanoTime());
        log.debug("필터칩 개수 집계 지역={} 콘텐츠={} 결과={}", all.size(), stored.size(), counted.byCategory());
        return counted;
    }
}
