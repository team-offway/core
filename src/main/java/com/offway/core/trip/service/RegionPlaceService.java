package com.offway.core.trip.service;

import com.offway.core.trip.domain.PlaceCategory;
import com.offway.core.trip.domain.PlaceKind;
import com.offway.core.trip.domain.TripException;
import com.offway.core.trip.repository.LicensedPlaceRepository;
import com.offway.core.trip.service.dto.RegionPlaces;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지역의 장소 목록 조회(#144) — 숙소·맛집·카페·관광명소를 종류별로 내려준다.
 *
 * <p>코스에 실린 몇 곳 말고 <b>그 지역에 무엇이 있는지 전부</b> 보고 싶다는 요구를 받는다. 전부 DB 에 있으므로
 * 외부 호출이 없다 — 외부 한도가 소진돼도, 포털이 점검 중이어도 목록은 나간다.
 */
@Service
@RequiredArgsConstructor
public class RegionPlaceService {

    /** 한 번에 내려줄 수 있는 최대 개수. 지역당 수천 건이라 상한이 없으면 한 요청이 전부를 끌어온다. */
    private static final int MAX_PAGE_SIZE = 100;

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final LicensedPlaceRepository licensedPlaceRepository;

    /**
     * 지역의 장소를 종류별로 조회한다.
     *
     * @param category 분류로 좁힐 때(예: 한옥체험만). null 이면 그 종류 전체
     * @throws TripException 분류가 종류에 속하지 않을 때(400)
     */
    @Transactional(readOnly = true)
    public RegionPlaces places(long regionId, PlaceKind kind, PlaceCategory category, Integer page, Integer size) {
        requireMatchingCategory(kind, category);

        // 같은 지역 안에서는 코스 적합도가 높은 분류를 먼저 보여준다. 목록 첫 화면에 다방·패스트푸드가
        // 깔리면 "여기 볼 게 없네" 로 읽힌다. 같은 순위끼리는 상호 가나다순으로 안정 정렬한다.
        //
        // 분류(enum)가 아니라 fitnessRank 로 정렬한다 — 분류는 문자열로 저장돼 사전순이 적합도 순서와
        // 다르다(LODGING 이 TOURIST_HOTEL 보다 앞선다). 페이징이 걸려 정렬은 DB 가 해야 한다.
        PageRequest pageRequest = PageRequest.of(
                page == null ? 0 : Math.max(page, 0),
                clampSize(size),
                Sort.by(Sort.Order.asc("fitnessRank"), Sort.Order.asc("name")));

        return RegionPlaces.from(licensedPlaceRepository.findPage(regionId, kind, category, pageRequest));
    }

    private static void requireMatchingCategory(PlaceKind kind, PlaceCategory category) {
        if (category != null && category.kind() != kind) {
            throw TripException.categoryKindMismatch();
        }
    }

    private static int clampSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.clamp(size, 1, MAX_PAGE_SIZE);
    }
}
