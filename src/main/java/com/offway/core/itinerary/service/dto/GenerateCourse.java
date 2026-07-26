package com.offway.core.itinerary.service.dto;

import com.offway.core.itinerary.domain.Density;
import com.offway.core.transport.domain.TransportMode;
import java.time.LocalDate;

/**
 * 코스 생성 커맨드 — 서비스 내부용(course-logic 입력). 후보지역(추천)에서 지역을 고른 뒤 위저드 값과 함께 넘어온다.
 *
 * @param regionId 코스를 만들 지역
 * @param travelDays 여행 일수(1~3)
 * @param density 일정 밀도(빡빡/널널)
 * @param transport 이동수단
 * @param originLat 출발지 위도(동선 정렬 기준)
 * @param originLng 출발지 경도
 * @param travelDate 가는 날(정책 운영기간 매칭용)
 */
public record GenerateCourse(
        long regionId,
        int travelDays,
        Density density,
        TransportMode transport,
        double originLat,
        double originLng,
        LocalDate travelDate) {
}
