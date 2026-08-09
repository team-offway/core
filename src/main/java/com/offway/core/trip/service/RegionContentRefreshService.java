package com.offway.core.trip.service;

import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.RegionContent;
import com.offway.core.trip.domain.StoredRegionContent;
import com.offway.core.trip.repository.RegionContentRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 지역 콘텐츠를 받아 DB 에 적재한다(#193 2단계).
 *
 * <p><b>왜 DB 인가.</b> 인메모리 캐시는 프로세스와 함께 죽어, 배포할 때마다 89개 지역을 처음부터 다시 긁었다.
 * 지역 콘텐츠는 #193 이 꼽은 넷 중 <b>호출량이 가장 크다</b> — 지역당 자기 + 인접 최대 3곳이라 한 번 워밍에
 * 130건 안팎이 나간다. 배포가 잦은 날이면 이것만으로 일일 한도(1,000건)를 넘긴다.
 *
 * <p><b>인접 병합까지 끝난 값을 저장한다.</b> 예전에는 요청 경로에서 팬아웃과 인접 50km 병합을 했다. 인접
 * 관계는 89곳 좌표에서 나오는 고정값이라 미리 계산해도 틀어지지 않고, 그만큼 요청 경로가 가벼워진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionContentRefreshService {

    /** 부팅 후 첫 적재까지 지연 — 기동·헬스체크를 방해하지 않게. */
    private static final String INITIAL_DELAY = "PT60S";

    /**
     * 갱신 주기.
     *
     * <p>원본(TourAPI 지역 콘텐츠)은 월 단위로도 잘 안 변한다. 하루 한 번이면 발행 주기보다 촘촘하고,
     * 89곳 × (자기 + 인접)이라 하루 130건 안팎으로 한도에 여유가 있다.
     */
    private static final String REFRESH_INTERVAL = "P1D";

    private final RegionContentProvider regionContentProvider;
    private final RegionContentRepository regionContentRepository;
    private final RegionRepository regionRepository;

    @Scheduled(initialDelayString = INITIAL_DELAY, fixedDelayString = REFRESH_INTERVAL)
    public void refresh() {
        List<Region> regions = regionRepository.findAll();
        if (regions.isEmpty()) {
            return;
        }
        // 팬아웃(동시성 상한·지역별 예외 격리·전체 시간 상한)은 provider 가 소유한다. 배치라 사용자를
        // 기다리게 하지 않으므로 워밍용 시간 예산을 쓴다.
        RegionContentProvider.RegionContents fetched = regionContentProvider.contentForAll(
                regions, regions, RegionContentProvider.WARMING_FANOUT_DEADLINE);
        Map<Long, RegionContent> byRegionId = fetched.byRegionId();
        if (byRegionId.isEmpty()) {
            // 빈 결과로 덮으면 전 지역 콘텐츠가 통째로 사라진다. 이전 적재가 낫다.
            log.warn("지역 콘텐츠 갱신 결과가 비어 적재를 건너뜁니다 — 이전 적재를 유지합니다(저장={}건)",
                    regionContentRepository.count());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        Map<Long, RegionContent> previous =
                regionContentProvider.storedForAll(regions.stream().map(Region::getId).toList());

        List<StoredRegionContent> rows = new ArrayList<>();
        int kept = 0;
        for (Region region : regions) {
            RegionContent fresh = byRegionId.get(region.getId());
            RegionContent stored = previous.get(region.getId());
            // 조회가 degrade 하면 provider 가 빈 콘텐츠를 준다. 그걸 그대로 넣으면 전량 교체라 <b>멀쩡하던
            // 지역이 빈 값으로 덮인다</b> — 갱신 실패가 데이터 손실이 되는 셈이다. 이전 값이 있으면 그것을 남긴다.
            boolean usable = fresh != null && !RegionContent.EMPTY.equals(fresh);
            if (!usable && stored != null) {
                rows.add(StoredRegionContent.of(region.getId(), stored, now));
                kept++;
                continue;
            }
            if (usable) {
                rows.add(StoredRegionContent.of(region.getId(), fresh, now));
            }
            // 새 값도 이전 값도 없으면 그 지역은 저장하지 않는다 — 호출자가 빈 콘텐츠로 취급한다.
        }
        regionContentRepository.replaceAll(rows);

        int missing = regions.size() - rows.size();
        if (missing > 0 || kept > 0 || fetched.degraded() > 0) {
            // 조용히 넘어가면 커버리지가 줄어든 것을 아무도 모른다(#191 과 같은 형식).
            log.warn("지역 콘텐츠 적재 완료 지역={}/{} — 이전 값 유지 {}건·못 채운 {}건·외부 degrade {}건",
                    rows.size(), regions.size(), kept, missing, fetched.degraded());
            return;
        }
        log.info("지역 콘텐츠 적재 완료 지역={}/{}", rows.size(), regions.size());
    }
}
