package com.offway.core.trip.infrastructure.tour.dto;

/**
 * TourAPI 공통 상세(detailCommon2) 파싱 결과 — 장소 하나의 기본 정보. 운영시간·휴무일({@link TourIntro})은 별도 조회.
 *
 * @param contentId 콘텐츠 ID
 * @param contentTypeId 콘텐츠 타입(운영시간 조회에 필요)
 * @param title 장소명
 * @param address 주소(없으면 null)
 * @param tel 전화(없으면 null)
 * @param lat 위도(없으면 null)
 * @param lng 경도(없으면 null)
 * @param imageUrl 대표 이미지(없으면 null)
 * @param overview 소개 문구(없으면 null)
 */
public record TourPoiDetail(
        String contentId,
        Integer contentTypeId,
        String title,
        String address,
        String tel,
        Double lat,
        Double lng,
        String imageUrl,
        String overview) {
}
