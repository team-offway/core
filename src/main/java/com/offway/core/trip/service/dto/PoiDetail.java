package com.offway.core.trip.service.dto;

/**
 * 장소 상세 — 공통정보(detailCommon2)와 소개정보(detailIntro2)를 합친 결과.
 *
 * @param contentId 콘텐츠 ID
 * @param contentTypeId 콘텐츠 타입
 * @param title 장소명
 * @param address 주소(없으면 null)
 * @param tel 전화(없으면 null)
 * @param lat 위도(없으면 null)
 * @param lng 경도(없으면 null)
 * @param imageUrl 대표 이미지(없으면 null)
 * @param overview 소개 문구(없으면 null)
 * @param useTime 이용/영업 시간(없으면 null)
 * @param restDate 휴무일(없으면 null)
 * @param catchphrase 구석구석 캐치프레이즈(감성 한 줄, 없으면 null)
 */
public record PoiDetail(
        String contentId,
        Integer contentTypeId,
        String title,
        String address,
        String tel,
        Double lat,
        Double lng,
        String imageUrl,
        String overview,
        String useTime,
        String restDate,
        String catchphrase) {
}
