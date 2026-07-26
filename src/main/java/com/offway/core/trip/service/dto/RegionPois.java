package com.offway.core.trip.service.dto;

import java.util.List;

/**
 * 한 지역의 코스 후보 POI 를 세 풀로 분류한 결과(course-logic ①: 볼거리풀·맛집풀·숙박풀). itinerary 가 이 풀에서 필요 수만큼
 * 골라 슬롯에 배치한다.
 *
 * @param sights 볼거리(관광지·문화·축제·레포츠)
 * @param foods 맛집(음식점)
 * @param stays 숙박
 */
public record RegionPois(List<PoiCandidate> sights, List<PoiCandidate> foods, List<PoiCandidate> stays) {

    public RegionPois {
        sights = List.copyOf(sights);
        foods = List.copyOf(foods);
        stays = List.copyOf(stays);
    }

    public static RegionPois empty() {
        return new RegionPois(List.of(), List.of(), List.of());
    }
}
