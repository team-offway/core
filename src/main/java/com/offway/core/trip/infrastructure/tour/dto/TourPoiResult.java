package com.offway.core.trip.infrastructure.tour.dto;

import java.util.List;

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
}
