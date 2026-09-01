package com.offway.core.trip.infrastructure.tour.dto;

import java.util.List;

/**
 * 축제 목록 한 페이지(#388).
 *
 * <p><b>{@code totalCount} 가 이 작업의 실측이다.</b> 첫 호출 하나가 "전국에 축제가 몇 건이고 몇 페이지를
 * 더 돌아야 하는지" 를 알려준다 — 페이지를 끝까지 돌아 보고서야 아는 것이 아니라, <b>한 번 부르면 남은
 * 비용이 확정된다.</b> 배치가 그 값을 보고 계속할지 멈출지 정한다.
 *
 * @param items 이번 페이지에서 날짜가 온전했던 축제들. 날짜가 깨진 행은 여기 없다
 * @param totalCount 조건에 맞는 전체 건수 — <b>이번 페이지 건수가 아니다</b>
 */
public record TourFestivalResult(List<TourFestival> items, int totalCount) {

    public TourFestivalResult {
        items = List.copyOf(items);
    }

    private static final TourFestivalResult EMPTY = new TourFestivalResult(List.of(), 0);

    /** 키 없음·조회 실패 등 비어 있는 결과. */
    public static TourFestivalResult empty() {
        return EMPTY;
    }

    /**
     * {@code rows} 건씩 나눌 때 전체 페이지 수.
     *
     * <p>배치가 <b>첫 응답만 보고</b> 남은 호출 수를 알기 위한 계산이다. 이 값이 예상보다 크면 상한에
     * 걸려 중단하고, 그 사실을 로그로 남긴다 — 조용히 잘린 결과를 "다 받았다" 로 읽지 않으려는 것이다.
     */
    public int totalPages(int rows) {
        if (rows <= 0) {
            throw new IllegalArgumentException("페이지 크기는 1 이상이어야 합니다: " + rows);
        }
        return (totalCount + rows - 1) / rows;
    }
}
