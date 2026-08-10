package com.offway.core.trip.service.dto;

import com.offway.core.trip.infrastructure.tour.dto.TourIntro;

/**
 * 장소 상세 — 공통 정보 + 카테고리별 보조정보(#157).
 *
 * <p>보조정보는 {@link TourIntro} 를 그대로 들고 다닌다. 여기서 카테고리별로 쪼개면 그 지식이 서비스와 응답
 * DTO 두 곳에 생긴다 — 쪼개는 일은 응답 DTO 가 한 번만 한다.
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
        /** 카테고리별 보조정보. 우리 DB 출처(인허가·국가유산)는 없어 null 이다. */
        TourIntro intro,
        String catchphrase) {

    /** 관광 API 콘텐츠가 아닌 장소 — 보조정보가 없다. */
    public static PoiDetail withoutIntro(
            String contentId, Integer contentTypeId, String title, String address, String tel,
            Double lat, Double lng, String imageUrl, String overview) {
        return new PoiDetail(contentId, contentTypeId, title, address, tel, lat, lng, imageUrl, overview, null, null);
    }
}
