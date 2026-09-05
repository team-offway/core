package com.offway.core.transport.service;

import com.offway.core.common.geo.Coordinate;
import java.util.List;

/**
 * 방문 순서 최적화 port — transport 가 코스(itinerary)에 노출한다. 하루 방문 지점들을 <b>실도로 기준 최단 동선</b>으로 재배열한다
 * (섬·산악처럼 직선거리와 도로가 크게 다른 곳에서 특히 효과).
 */
public interface RouteOptimizer {

    /**
     * 방문 지점들을 최적 순서로 재배열한 인덱스 리스트. TMAP 실측 최적화를 우선 쓰고, 불가 시 직선거리 최근접으로 폴백한다.
     *
     * @param points 방문 지점(첫 지점은 출발로 고정)
     * @return 입력 인덱스를 방문 순서대로 나열한 리스트(입력과 같은 크기)
     */
    List<Integer> optimalOrder(List<Coordinate> points);
}
