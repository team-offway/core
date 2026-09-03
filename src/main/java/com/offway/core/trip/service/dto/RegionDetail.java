package com.offway.core.trip.service.dto;

import com.offway.core.trip.domain.RegionPoi;
import java.util.List;
import lombok.Builder;

/**
 * 지역 상세 화면이 한 번에 필요한 것(#304) — 지역 소개 + 대표 사진 + 매력 포인트 장소.
 *
 * <p>화면 하나를 채우는 데 왕복이 여러 번이면 그만큼 느려지고, 그 사이 화면이 조각조각 채워진다.
 * 이 서비스가 붙는 이유가 그것이라 응답도 한 덩어리다.
 *
 * @param sido 시도명. 표기 조합({@code 시군구 · 시도})은 응답 DTO 가 한다 — 서비스가 화면 문구를 들지 않는다
 * @param overview 지역 한 줄 소개(#140). <b>없으면 null</b> — 앱이 그 칸을 접는다
 * @param photos 대표 이미지. 없으면 빈 목록이고, 지금은 한 장이 최대다
 * @param benefit 이 지역에 걸리는 혜택. 없으면 null
 * @param highlightSpots 매력 포인트 장소 — <b>사진 있는 것만</b> 담긴다
 */
@Builder
public record RegionDetail(
        long regionId,
        String sido,
        String sigungu,
        String overview,
        List<String> photos,
        RegionBenefit benefit,
        List<Spot> highlightSpots) {

    /**
     * 매력 포인트 장소 하나.
     *
     * @param catchphrase 구석구석 한 줄 소개(#87). <b>없을 수 있다</b> — 4.5만 건 CSV 에 그 contentId 가
     *     없으면 비고, 그때는 앱이 이름만 그린다
     */
    public record Spot(String poiContentId, String name, String imageUrl, String catchphrase) {

        public static Spot of(RegionPoi poi, String catchphrase) {
            return new Spot(poi.getContentId(), poi.getTitle(), poi.getImageUrl(), catchphrase);
        }
    }

    public RegionDetail {
        photos = List.copyOf(photos);
        highlightSpots = List.copyOf(highlightSpots);
    }
}
