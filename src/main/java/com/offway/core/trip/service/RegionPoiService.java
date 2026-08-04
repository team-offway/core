package com.offway.core.trip.service;

import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.LicensedPlace;
import com.offway.core.trip.domain.PlaceKind;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourPoi;
import com.offway.core.trip.service.dto.PoiCandidate;
import com.offway.core.trip.repository.LicensedPlaceRepository;
import com.offway.core.trip.service.dto.RegionPois;
import java.util.ArrayList;
import java.util.Comparator;
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

    /** 인허가 장소 식별자 접두어 — TourAPI contentId 와 섞이지 않게 한다. */
    private static final String LICENSED_ID_PREFIX = "LIC-";

    /** TourAPI 콘텐츠가 아님을 뜻하는 타입. 실제 contentTypeId 는 12·32·39 처럼 모두 양수다. */
    private static final int LICENSED_CONTENT_TYPE = 0;

    private final RegionRepository regionRepository;
    private final TourApiClient tourApiClient;
    private final CatchphraseProvider catchphraseProvider;
    private final LicensedPlaceRepository licensedPlaceRepository;

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

        RegionPois pois = new RegionPois(sights, foods, stays);
        log.info("코스 POI 수집 regionId={} 볼거리={} 맛집={} 숙박={}", regionId, sights.size(), foods.size(), stays.size());
        return pois.needsSupplement() ? supplement(pois, regionId) : pois;
    }

    /**
     * TourAPI 가 못 채운 풀을 인허가 데이터로 메운다(#144).
     *
     * <p>TourAPI 숙박은 관광사업체 위주라 지방 숙소가 거의 없다 — 의성군이 1건이다. 그대로 두면 슬롯이 조용히 빠져
     * "잘 곳 없는 2박3일" 이 200 으로 나간다. 인허가 데이터엔 사진·소개가 없으므로 <b>부족할 때만</b> 쓴다.
     */
    private RegionPois supplement(RegionPois pois, long regionId) {
        RegionPois supplemented = pois.supplementedWith(
                licensedCandidates(regionId, PlaceKind.SIGHT),
                licensedCandidates(regionId, PlaceKind.FOOD),
                licensedCandidates(regionId, PlaceKind.STAY));

        // degrade 한 사실을 남긴다 — 보충이 조용히 일어나면 TourAPI 쪽 공백을 아무도 모른다.
        log.info("인허가 데이터로 보충 regionId={} 볼거리={}→{} 맛집={}→{} 숙박={}→{}",
                regionId,
                pois.sights().size(), supplemented.sights().size(),
                pois.foods().size(), supplemented.foods().size(),
                pois.stays().size(), supplemented.stays().size());
        return supplemented;
    }

    /**
     * 인허가 장소를 후보로 옮긴다. 관광 콘텐츠성이 높은 분류(한옥·사찰·박물관)를 앞에 두어, 모자란 만큼만 쓰일 때
     * 더 나은 쪽이 먼저 뽑히게 한다.
     */
    private List<PoiCandidate> licensedCandidates(long regionId, PlaceKind kind) {
        return licensedPlaceRepository.findByRegionAndKind(regionId, kind).stream()
                .sorted(Comparator.comparing(LicensedPlace::isPreferred).reversed())
                .map(RegionPoiService::toCandidate)
                .toList();
    }

    /**
     * 인허가 장소는 TourAPI 콘텐츠가 아니라 상세 조회 대상이 아니다. 식별자에 접두어를 붙여 두 출처를 구분할 수 있게 하고,
     * 콘텐츠 타입은 "TourAPI 아님" 을 뜻하는 0 으로 둔다.
     */
    private static PoiCandidate toCandidate(LicensedPlace place) {
        return new PoiCandidate(
                LICENSED_ID_PREFIX + place.getId(),
                LICENSED_CONTENT_TYPE,
                place.getName(),
                place.getLat(),
                place.getLng(),
                null, // 인허가 데이터에는 사진이 없다
                place.getAddress(),
                null); // 캐치프레이즈도 TourAPI 콘텐츠에만 붙는다
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
