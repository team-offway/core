package com.offway.core.trip.controller.dto;

import com.offway.core.trip.domain.PoiContentType;
import com.offway.core.trip.service.dto.PoiDetail;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 장소 상세 응답 — API 계약. 코스 타임라인에서 장소를 누르면 보는 상세.
 *
 * @param contentId 콘텐츠 ID
 * @param contentTypeId 콘텐츠 타입 코드
 * @param typeLabel 콘텐츠 타입 한글 라벨(관광지·음식점 등)
 * @param title 장소명
 * @param address 주소(없으면 null)
 * @param tel 전화(없으면 null)
 * @param lat 위도(없으면 null)
 * @param lng 경도(없으면 null)
 * @param imageUrl 대표 이미지(없으면 null)
 * @param overview 소개 문구(없으면 null)
 * @param useTime 이용/영업 시간(없으면 null)
 * @param restDate 휴무일(없으면 null)
 */
public record PoiDetailResponse(
        String contentId,
        Integer contentTypeId,
        @Schema(example = "관광지") String typeLabel,
        @Schema(example = "완도타워 전망대") String title,
        String address,
        String tel,
        Double lat,
        Double lng,
        String imageUrl,
        String overview,
        @Schema(example = "09:00~18:00") String useTime,
        @Schema(example = "연중무휴") String restDate) {

    public static PoiDetailResponse from(PoiDetail poi) {
        return new PoiDetailResponse(
                poi.contentId(),
                poi.contentTypeId(),
                PoiContentType.labelOf(poi.contentTypeId()),
                poi.title(),
                poi.address(),
                poi.tel(),
                poi.lat(),
                poi.lng(),
                poi.imageUrl(),
                poi.overview(),
                poi.useTime(),
                poi.restDate());
    }
}
