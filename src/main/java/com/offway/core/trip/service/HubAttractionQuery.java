package com.offway.core.trip.service;

import com.offway.core.trip.domain.HubAttraction;
import com.offway.core.trip.domain.Popularity;
import com.offway.core.trip.repository.HubAttractionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "그 지역에서 많이 찾는 순서" 를 코스에 알려주는 통로(#527).
 *
 * <p>{@link RelatedAttractionQuery} 와 같은 자리다 — itinerary 는 이 서비스로만 중심관광지를 얻고
 * trip 의 저장소를 직접 보지 않는다.
 *
 * <p><b>DB 만 읽는다.</b> 적재는 월 1회 배치({@code HubAttractionRefreshService})가 하고, 여기는
 * 요청 경로라 외부를 부르지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HubAttractionQuery {

    private final HubAttractionRepository hubAttractionRepository;

    /**
     * 그 지역 볼거리의 인기 순위.
     *
     * <p>볼거리만 남긴다. 중심관광지의 대분류는 <b>관광지와 숙박 둘뿐</b>이고(실측 2,669건: 관광지
     * 2,174 · 숙박 495) <b>음식·카페는 아예 없다</b> — 그래서 인기순은 볼거리에만 붙는다. 무엇이
     * 볼거리인지는 {@link HubAttraction#isSight()} 가 소유한다.
     *
     * @return 중심관광지가 없으면 {@link Popularity#none()} — 호출자는 다음 근거로 넘어간다
     */
    @Transactional(readOnly = true)
    public Popularity sightPopularity(long regionId) {
        List<HubAttraction> hubs = hubAttractionRepository.findByRegionId(regionId).stream()
                .filter(HubAttraction::isSight)
                .toList();
        if (hubs.isEmpty()) {
            log.debug("중심관광지가 없어 인기순을 못 씁니다 regionId={}", regionId);
            return Popularity.none();
        }
        return Popularity.of(hubs);
    }
}
