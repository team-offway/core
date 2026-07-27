package com.offway.core.transport.service;

import com.offway.core.transport.domain.Coordinate;

/**
 * 자동차 <b>실측 구간 이동시간</b> port — transport 가 코스(itinerary)에 노출한다. 코스 타임라인의 이웃 장소 사이 실제 소요시간을
 * 채우는 데 쓴다.
 *
 * <p>{@link TravelTimeProvider}(직선거리 근사)와 역할이 다르다: 저건 추천의 <i>대량·저해상</i> 도달필터용(전 지역 반복 호출),
 * 이건 코스의 <i>소수·고해상</i> 구간 실측용(TMAP 한도 안에서 이웃 구간만). 그래서 port 를 분리한다.
 */
public interface RouteTimeProvider {

    /** 출발→목적지 자동차 실측 이동시간(분). TMAP 우선, 불가 시 직선거리 근사로 폴백. */
    int drivingMinutes(Coordinate from, Coordinate to);
}
