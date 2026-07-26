package com.offway.core.trip.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 지역의 볼거리 콘텐츠 요약(F3) — 볼거리 수·대표 이미지·categories. 콘텐츠가 충분한지 스스로 판단하고, 부족하면 인접 지역 콘텐츠와의
 * 병합을 스스로 표현한다(rich domain). 서비스는 조율만 한다.
 *
 * @param contentCount 볼거리 수(TourAPI totalCount). 인접 병합 시 합산된 값
 * @param imageUrl 대표 이미지 URL(없으면 null)
 * @param categories 이 지역에 존재하는 카테고리(무드칩과 동일 분류, 선언 순서 유지·중복 제거)
 * @param neighborIncluded 인접 50km 지역 콘텐츠가 병합됐는지(UI "인접 포함" 안내용)
 */
public record RegionContent(int contentCount, String imageUrl, List<Category> categories, boolean neighborIncluded) {

    /** 콘텐츠 충분 기준 — 볼거리가 이 미만이면 인접 50km 지역과 묶는다(feature-spec F4: {@code <9} 확장). */
    private static final int SUFFICIENCY_MIN = 9;

    /** 콘텐츠가 없는 지역(키 없음·조회 0건). */
    public static final RegionContent EMPTY = new RegionContent(0, null, List.of(), false);

    public RegionContent {
        categories = List.copyOf(categories);
    }

    /** 볼거리가 충분해 인접 확장이 필요 없는가. */
    public boolean isSufficient() {
        return contentCount >= SUFFICIENCY_MIN;
    }

    /**
     * 인접 지역 콘텐츠를 이 지역에 병합한다 — 볼거리 수는 합산, categories 는 합집합(순서 유지), 대표 이미지는 이쪽이 없을 때만 인접 것으로
     * 폴백. 병합이 실제로 콘텐츠를 더했으면 {@code neighborIncluded} 를 세운다.
     */
    public RegionContent expandedWith(RegionContent neighbor) {
        if (neighbor.contentCount == 0 && neighbor.categories.isEmpty()) {
            return this; // 인접도 비어 있으면 병합 표시를 세우지 않는다
        }
        Set<Category> merged = new LinkedHashSet<>(categories);
        merged.addAll(neighbor.categories);
        return new RegionContent(
                contentCount + neighbor.contentCount,
                imageUrl != null ? imageUrl : neighbor.imageUrl,
                new ArrayList<>(merged),
                true);
    }
}
