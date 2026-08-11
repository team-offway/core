package com.offway.core.trip.service.dto;

import com.offway.core.trip.domain.PoiIntro;

/**
 * 장소 상세 — 공통 정보 + 카테고리별 보조정보(#157).
 *
 * <p>보조정보는 {@link PoiIntro} 를 통째로 들고 다닌다. 여기서 카테고리별로 쪼개면 그 지식이 서비스와 응답
 * DTO 두 곳에 생긴다 — 쪼개는 일은 응답 DTO 가 한 번만 한다.
 *
 * <p>외부 어댑터의 DTO 가 아니라 도메인 타입을 든다. 관광 API 응답 모양이 바뀌어도 이 계약은 흔들리지 않는다.
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
        PoiIntro intro,
        /**
         * 지도 검색 링크(#161) — 우리가 영업시간·사진을 못 주는 장소를 지도로 넘긴다.
         *
         * <p>관광 API 콘텐츠에는 붙이지 않는다. 그쪽은 사진·소개·운영시간이 우리 응답에 이미 들어 있어,
         * 링크를 함께 주면 사용자가 어디를 봐야 할지 갈린다.
         */
        String mapSearchUrl,
        String catchphrase) {

    /** 관광 API 콘텐츠가 아닌 장소 — 보조정보가 없다. */
    public static PoiDetail withoutIntro(
            String contentId, Integer contentTypeId, String title, String address, String tel,
            Double lat, Double lng, String imageUrl, String overview, String mapSearchUrl) {
        return new PoiDetail(
                contentId, contentTypeId, title, address, tel, lat, lng, imageUrl, overview, null, mapSearchUrl, null);
    }
}
