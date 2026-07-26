package com.offway.core.trip.service;

import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourPoi;
import com.offway.core.trip.service.dto.PoiCandidate;
import com.offway.core.trip.service.dto.RegionPois;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 지역의 코스 후보 POI 를 모아 볼거리·맛집·숙박 풀로 분류한다(course-logic ①). itinerary 가 코스를 짤 때 이 port 로만 POI 를
 * 얻는다 — 다른 도메인이 TourAPI 를 직접 부르지 않는다(도메인 경계).
 *
 * <p>TourAPI 는 read-timeout 이 길어 <b>트랜잭션 밖</b>에서 호출한다(persistence-convention). 키가 없으면 빈 결과
 * (로컬 실행성).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionPoiService {

    /** 한 번에 끌어오는 후보 수 — 분류 후 itinerary 가 필요 수만큼 고른다. */
    private static final int CANDIDATE_ROWS = 100;

    // TourAPI contentTypeId → 풀. (12 관광지·14 문화시설·15 축제공연행사·28 레포츠 = 볼거리, 39 음식점, 32 숙박)
    private static final Set<Integer> SIGHT_TYPES = Set.of(12, 14, 15, 28);
    private static final int FOOD_TYPE = 39;
    private static final int STAY_TYPE = 32;

    private final RegionRepository regionRepository;
    private final TourApiClient tourApiClient;

    /** 지역의 후보 POI 를 세 풀로 분류해 돌려준다. 좌표가 없는 POI 는 지도·동선에 못 쓰므로 제외한다. */
    public RegionPois collect(long regionId) {
        List<Region> found = regionRepository.findByIds(List.of(regionId));
        if (found.isEmpty()) {
            log.info("코스 POI 수집 — 없는 지역 regionId={}", regionId);
            return RegionPois.empty();
        }
        Region region = found.get(0);

        List<PoiCandidate> sights = new ArrayList<>();
        List<PoiCandidate> foods = new ArrayList<>();
        List<PoiCandidate> stays = new ArrayList<>();
        for (TourPoi poi : tourApiClient.findByArea(
                region.getAreaCode(), region.getSigunguCode(), null, CANDIDATE_ROWS).items()) {
            PoiCandidate candidate = toCandidate(poi);
            if (candidate == null) {
                continue; // 좌표·필수값 결여
            }
            classify(candidate, sights, foods, stays);
        }
        log.info("코스 POI 수집 regionId={} 볼거리={} 맛집={} 숙박={}", regionId, sights.size(), foods.size(), stays.size());
        return new RegionPois(sights, foods, stays);
    }

    private static void classify(
            PoiCandidate c, List<PoiCandidate> sights, List<PoiCandidate> foods, List<PoiCandidate> stays) {
        if (SIGHT_TYPES.contains(c.contentTypeId())) {
            sights.add(c);
        } else if (c.contentTypeId() == FOOD_TYPE) {
            foods.add(c);
        } else if (c.contentTypeId() == STAY_TYPE) {
            stays.add(c);
        }
    }

    private static PoiCandidate toCandidate(TourPoi poi) {
        if (poi.contentTypeId() == null || poi.contentId() == null || poi.title() == null
                || poi.lat() == null || poi.lng() == null) {
            return null;
        }
        return new PoiCandidate(
                poi.contentId(), poi.contentTypeId(), poi.title(), poi.lat(), poi.lng(), poi.firstImage());
    }
}
