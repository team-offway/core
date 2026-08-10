package com.offway.core.trip.service;

import com.offway.core.common.batch.repository.BatchRunRepository;
import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.RegionContent;
import com.offway.core.trip.domain.StoredRegionContent;
import com.offway.core.trip.repository.RegionContentRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
     * 갱신 주기 — <b>주 1회</b>.
     *
     * <p>원본(TourAPI 지역 콘텐츠)은 월 단위로도 잘 안 변한다. 같은 이유로 관광사진 갤러리도 이미 주 1회다.
     *
     * <p><b>하루 한 번이던 것을 주 1회로 늘렸다.</b> "하루 130건이면 한도에 여유가 있다" 고 봤는데, 그 계산이
     * 부팅마다 도는 것을 세지 않았다. 배포가 잦은 날은 130 × 배포 횟수가 되어 실제로 TourAPI 일일 한도를
     * 태웠다({@code LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR}).
     */
    private static final String REFRESH_INTERVAL = "P7D";

    /** 배치 식별자 — {@code batch_run} 의 키다. 바꾸면 "한 번도 안 돈 것" 이 되어 그 주에 한 번 더 돈다. */
    static final String BATCH_NAME = "region-content-refresh";

    /** 실행 간격 — 위 주기와 같은 값이어야 한다. 재부팅이 이 창 안이면 외부를 아예 안 부른다. */
    private static final Duration MIN_INTERVAL = Duration.ofDays(7);

    /** "지금" 판정은 KST — 저장 시각도 같은 기준이라야 한다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final RegionContentProvider regionContentProvider;
    private final RegionContentRepository regionContentRepository;
    private final RegionRepository regionRepository;
    private final BatchRunRepository batchRunRepository;

    /**
     * 주 1회 — <b>그 주에 이미 돌았으면</b> 외부를 아예 안 부른다.
     *
     * <p>예전에는 건너뛰기가 없어 부팅할 때마다 89곳 × (자기 + 인접) ≈ 130회를 다시 쐈다. 배포가 잦은 날은
     * 그만큼 곱해져 TourAPI 일일 한도를 태웠고, 그러면 코스 생성·장소 상세까지 함께 막힌다 —
     * 같은 키를 여러 API 가 공유하기 때문이다.
     *
     * <p><b>결과가 아니라 실행을 기록한다.</b> 적재 결과로 판정하면 전부 실패한 회차에는 아무것도 안 써져
     * 다음 부팅이 또 130회를 쏜다({@code HubAttractionRefreshService} 가 정확히 그랬다, #226).
     */
    @Scheduled(initialDelayString = INITIAL_DELAY, fixedDelayString = REFRESH_INTERVAL)
    public void refreshIfStale() {
        LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
        if (batchRunRepository.hasRunSince(BATCH_NAME, now.minus(MIN_INTERVAL))) {
            log.info("지역 콘텐츠를 최근 {}에 이미 갱신해 건너뜁니다", MIN_INTERVAL);
            return;
        }
        // 실패해도 남긴다 — 안 남기면 같은 주에 재부팅마다 130회를 다시 쏜다.
        batchRunRepository.markStarted(BATCH_NAME, now);
        refresh();
    }

    /**
     * 전 지역을 다시 받아 적재한다 — <b>건너뛰기 없이</b>.
     *
     * <p>주기 판단은 {@link #refreshIfStale} 이 소유한다. 여기를 직접 부르면 무조건 89곳 × (자기 + 인접)을
     * 쏘므로, 운영에서는 스케줄러만 이 경로를 탄다.
     */
    public void refresh() {
        List<Region> regions = regionRepository.findAll();
        if (regions.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
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
