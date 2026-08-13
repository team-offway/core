package com.offway.core.region.service;

import com.offway.core.region.domain.Region;
import com.offway.core.region.domain.RegionIntro;
import com.offway.core.trip.repository.RegionLandmarkRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 지역 한 줄 소개를 <b>부팅 시 한 번</b> 만들어 들고 있는다(#140).
 *
 * <p>재료(국가유산·인허가 볼거리)는 배포 파일에서 오는 레퍼런스라 프로세스가 사는 동안 바뀌지 않는다.
 * 요청마다 89곳을 다시 조립할 이유가 없다 — 지역 마스터(#102)와 같은 이유다.
 *
 * <p><b>국가유산이 먼저다.</b> 국보·사적·명승은 그 자체가 지역을 대표하는 자리다. 국가유산이 없는 지역
 * (실측 1곳 — 대구 서구)만 인허가 볼거리로 대신한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegionIntroProvider {

    /** 소개에 쓸 이름 수. 둘이면 "A와 B가 있는 곳", 하나면 "A가 있는 곳". */
    private static final int NAMES_PER_REGION = 2;

    private final RegionMaster regionMaster;
    private final RegionLandmarkRepository landmarkRepository;

    private volatile Map<Long, RegionIntro> introById = Map.of();

    /** 기동이 끝난 뒤 만든다. 적재(장소 풀·국가유산)가 끝난 뒤여야 재료가 있다. */
    @EventListener(ApplicationReadyEvent.class)
    public void warm() {
        rebuild();
    }

    /** 이 지역의 소개. 재료가 없으면 텍스트가 {@code null} 이라 응답에서 필드가 사라진다. */
    public RegionIntro of(long regionId) {
        if (introById.isEmpty()) {
            rebuild();
        }
        return introById.getOrDefault(regionId, new RegionIntro(null));
    }

    private void rebuild() {
        List<Region> regions = regionMaster.all();
        if (regions.isEmpty()) {
            return;
        }
        Map<Long, List<String>> heritage = landmarkRepository.topHeritageNames(NAMES_PER_REGION);
        Map<Long, List<String>> licensed = landmarkRepository.topLicensedSightNames(NAMES_PER_REGION);

        Map<Long, RegionIntro> built = new HashMap<>();
        int empty = 0;
        for (Region region : regions) {
            List<String> names = heritage.getOrDefault(region.getId(),
                    licensed.getOrDefault(region.getId(), List.of()));
            RegionIntro intro = RegionIntro.of(region.getSigungu(), names);
            built.put(region.getId(), intro);
            if (intro.isEmpty()) {
                empty++;
            }
        }
        introById = Map.copyOf(built);
        if (empty > 0) {
            // 조용히 넘어가면 어느 지역 카드가 비는지 아무도 모른다.
            log.warn("지역 소개 조립 완료 {}/{} — 재료가 없어 빈 지역 {}곳", regions.size() - empty, regions.size(), empty);
            return;
        }
        log.info("지역 소개 조립 완료 {}곳", regions.size());
    }
}
