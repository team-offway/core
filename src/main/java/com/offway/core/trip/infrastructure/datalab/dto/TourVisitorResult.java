package com.offway.core.trip.infrastructure.datalab.dto;

import java.util.List;

/**
 * 관광빅데이터 방문자수 조회 결과.
 *
 * @param items 방문자수 레코드 (시군구 × 일자 × 방문자구분)
 * @param totalCount 전체 건수 (페이지네이션 판단)
 */
public record TourVisitorResult(List<RegionVisitor> items, int totalCount) {

    /** 외부에서 넘어온 가변 리스트 변경에 흔들리지 않게 방어적 복사. */
    public TourVisitorResult {
        items = List.copyOf(items);
    }

    private static final TourVisitorResult EMPTY = new TourVisitorResult(List.of(), 0);

    /** 키 없음·결과 없음 등 비어있는 결과. */
    public static TourVisitorResult empty() {
        return EMPTY;
    }
}
