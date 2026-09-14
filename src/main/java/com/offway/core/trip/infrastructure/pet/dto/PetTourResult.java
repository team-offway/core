package com.offway.core.trip.infrastructure.pet.dto;

import java.util.List;

/**
 * 반려동반 전국 목록 조회 결과(#566).
 *
 * <h2>페이지를 나누지 않는다</h2>
 *
 * <p>전국 9,679건이 <b>한 요청에</b> 온다(실측 2026-09-13: 6.3MB · 2,790ms). 나누면 호출 수만큼 실패
 * 지점이 늘고 "일부만 받은 회차" 라는 상태가 생긴다 — 월 1회 배치가 잠깐 쓰는 메모리라 그 복잡도를
 * 살 이유가 없다({@code GoCampsiteResult} 와 같은 판단).
 *
 * <h2>지역 파라미터 없이 받는다</h2>
 *
 * <p>이 API 는 <b>요청에만</b> TourAPI 지역코드가 필요하다(#555 함정 3). 지역을 빼고 전국을 받으면
 * 그 제약이 사라지고, 응답의 법정동 코드로 우리 89곳과 직접 맞출 수 있다 — 변환 누락이 없어져
 * 89곳 매칭이 170 → 442건이 됐다.
 *
 * @param places 쓸 수 있는 장소(매칭 키·지역을 아는 것만)
 * @param totalCount 원본이 말한 전체 건수 — 받은 수와 견줘 잘림을 판정한다
 */
public record PetTourResult(List<PetTourPlace> places, int totalCount) {

    public PetTourResult {
        places = List.copyOf(places);
    }

    /** 키가 없어 부르지 않았거나, 결과가 비어 온 회차. */
    public static PetTourResult empty() {
        return new PetTourResult(List.of(), 0);
    }

    public boolean isEmpty() {
        return places.isEmpty();
    }
}
