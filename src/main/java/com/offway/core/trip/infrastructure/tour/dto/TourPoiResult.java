package com.offway.core.trip.infrastructure.tour.dto;

import com.offway.core.trip.domain.Category;
import com.offway.core.trip.domain.RegionContent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * TourAPI 목록 조회 결과.
 *
 * @param items 이번 페이지의 관광지 목록
 * @param totalCount 전체 건수 — 지역 콘텐츠 충분성 판단(F3)에 쓴다
 */
public record TourPoiResult(List<TourPoi> items, int totalCount) {

    /** 외부에서 넘어온 가변 리스트가 바뀌어도 결과가 안 흔들리게 방어적 복사. */
    public TourPoiResult {
        items = List.copyOf(items);
    }

    private static final TourPoiResult EMPTY = new TourPoiResult(List.of(), 0);

    /** 키 없음·결과 없음 등 비어있는 결과. */
    public static TourPoiResult empty() {
        return EMPTY;
    }

    /**
     * 외부 응답을 도메인 콘텐츠로 변환한다(볼거리 수·대표 이미지·categories). 대표 이미지는 이미지가 있는 첫 POI, categories 는
     * 표본 POI 의 lclsSystm 대분류를 칩으로 매핑(중복 제거·발견 순서 유지)한다.
     */
    public RegionContent toRegionContent() {
        String imageUrl = items.stream()
                .map(TourPoi::firstImage)
                .filter(image -> image != null)
                .findFirst()
                .orElse(null);
        Set<Category> categories = new LinkedHashSet<>();
        for (TourPoi poi : items) {
            Category.fromLclsSystm1(poi.lclsSystm1()).ifPresent(categories::add);
        }
        return new RegionContent(totalCount, imageUrl, new ArrayList<>(categories), false);
    }
}
