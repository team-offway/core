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
    private final CatchphraseProvider catchphraseProvider;

    /** 지역의 후보 POI 를 세 풀로 분류해 돌려준다. 좌표가 없는 POI 는 지도·동선에 못 쓰므로 제외한다. */
    public RegionPois collect(long regionId) {
        List<Region> found = regionRepository.findByIds(List.of(regionId));
        if (found.isEmpty()) {
            log.info("코스 POI 수집 — 없는 지역 regionId={}", regionId);
            return RegionPois.empty();
        }
        Region region = found.get(0);

        // 볼거리·맛집·숙박을 각각 타입 스코프로 조회한다. 전체타입을 한 번만 뽑으면 인구감소지역처럼 등록 수가 적은 곳에서
        // 맛집·숙박이 볼거리에 밀려 과소표집돼(끼니·숙소가 코스에서 빠짐), 풀마다 독립 조회로 채운다.
        List<PoiCandidate> sights = candidates(region, null).stream()
                .filter(c -> SIGHT_TYPES.contains(c.contentTypeId()))
                .toList();
        List<PoiCandidate> foods = candidates(region, FOOD_TYPE);
        List<PoiCandidate> stays = candidates(region, STAY_TYPE);

        log.info("코스 POI 수집 regionId={} 볼거리={} 맛집={} 숙박={}", regionId, sights.size(), foods.size(), stays.size());
        return new RegionPois(sights, foods, stays);
    }

    /** 한 콘텐츠 타입(또는 {@code null}=전체)의 후보를 좌표 있는 것만 뽑는다. */
    private List<PoiCandidate> candidates(Region region, Integer contentTypeId) {
        List<PoiCandidate> out = new ArrayList<>();
        for (TourPoi poi : tourApiClient
                .findByArea(region.getAreaCode(), region.getSigunguCode(), contentTypeId, CANDIDATE_ROWS)
                .items()) {
            PoiCandidate candidate = toCandidate(poi);
            if (candidate != null) {
                out.add(candidate); // 좌표·필수값 결여는 제외
            }
        }
        return out;
    }

    private PoiCandidate toCandidate(TourPoi poi) {
        if (poi.contentTypeId() == null || poi.contentId() == null || poi.title() == null
                || poi.lat() == null || poi.lng() == null) {
            return null;
        }
        // 추천 한 줄(catchphrase)·주소는 코스 슬롯을 트리플식으로 인라인 렌더하기 위한 표시 정보다.
        return new PoiCandidate(
                poi.contentId(), poi.contentTypeId(), poi.title(), poi.lat(), poi.lng(),
                poi.firstImage(), poi.address(), catchphraseProvider.forContentId(poi.contentId()).orElse(null));
    }
}
