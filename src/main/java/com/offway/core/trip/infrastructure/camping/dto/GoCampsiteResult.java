package com.offway.core.trip.infrastructure.camping.dto;

import java.util.List;

/**
 * 고캠핑 조회 결과(#510).
 *
 * <p><b>페이지가 없다.</b> 전국 3,115건이 한 요청에 다 오므로({@code numOfRows=4000}) 나눠 받을 이유가
 * 없다. 나누면 호출 수만큼 실패 지점이 늘고, "일부만 받은 회차" 라는 상태가 생겨 취소 정리가 그것을
 * 온전한 회차로 오인할 여지가 생긴다 — 축제(#506)가 파일 방식으로 옮기며 걷어낸 그 복잡도다.
 *
 * @param items 쓸 수 있는 야영장 — 휴장·좌표 없음은 어댑터가 이미 걸렀다
 * @param totalCount 외부가 말한 전체 건수. 우리가 읽은 수와 어긋나면 응답이 잘린 것이다
 */
public record GoCampsiteResult(List<GoCampsite> items, int totalCount) {

    private static final GoCampsiteResult EMPTY = new GoCampsiteResult(List.of(), 0);

    public GoCampsiteResult {
        items = List.copyOf(items);
    }

    /** 키 없음·결과 없음 등 비어 있는 결과. */
    public static GoCampsiteResult empty() {
        return EMPTY;
    }
}
