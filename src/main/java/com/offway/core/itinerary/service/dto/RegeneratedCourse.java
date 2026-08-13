package com.offway.core.itinerary.service.dto;

/**
 * 재생성 결과(#114) — 새 코스와, 그것이 <b>정말 달라졌는지</b>.
 *
 * @param course 새로 짠 코스
 * @param seed 이 코스를 만든 씨앗. 같은 씨앗을 다시 주면 같은 코스가 나온다(재현성)
 * @param differentFromPrevious 직전 코스와 충분히 달라졌는가. 거짓이면 후보가 모자라 더 다르게 만들지 못한 것이다
 * @param overlapRatio 직전 코스와 겹친 장소 비율(0~1)
 */
public record RegeneratedCourse(
        GeneratedCourse course, long seed, boolean differentFromPrevious, double overlapRatio) {
}
