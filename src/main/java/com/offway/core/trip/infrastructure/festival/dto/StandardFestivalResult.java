package com.offway.core.trip.infrastructure.festival.dto;

import java.util.List;

/**
 * 전국문화축제표준데이터 조회 결과(#433).
 *
 * @param items 이 페이지의 축제
 * @param totalCount 전체 건수 — 첫 응답 하나로 남은 페이지 수가 확정된다
 */
public record StandardFestivalResult(List<StandardFestival> items, int totalCount) {

    private static final StandardFestivalResult EMPTY = new StandardFestivalResult(List.of(), 0);

    public StandardFestivalResult {
        items = List.copyOf(items);
    }

    /** 키 없음·결과 없음 등 비어 있는 결과. */
    public static StandardFestivalResult empty() {
        return EMPTY;
    }

    /** 이 건수를 다 받으려면 몇 페이지가 필요한가. */
    public int totalPages(int rowsPerPage) {
        return (totalCount + rowsPerPage - 1) / rowsPerPage;
    }
}
