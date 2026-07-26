package com.offway.core.trip.service.dto;

/**
 * 코스 생성이 배치할 후보 장소 한 건 — trip 이 TourAPI 에서 모아 다른 도메인(itinerary)에 넘기는 값. 어떤 풀(볼거리·맛집·숙박)에
 * 담기는지는 {@link RegionPois} 의 리스트 소속으로 표현한다(종류 필드 대신).
 *
 * @param contentId TourAPI 콘텐츠 ID
 * @param contentTypeId TourAPI 콘텐츠 타입
 * @param title 장소명
 * @param lat 위도
 * @param lng 경도
 * @param imageUrl 대표 이미지(없으면 null)
 */
public record PoiCandidate(
        String contentId, int contentTypeId, String title, double lat, double lng, String imageUrl) {
}
