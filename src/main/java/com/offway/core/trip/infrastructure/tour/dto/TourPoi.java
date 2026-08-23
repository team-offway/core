package com.offway.core.trip.infrastructure.tour.dto;

/**
 * TourAPI 목록 응답의 관광지(POI) 한 건. 외부 응답을 파싱한 결과.
 *
 * @param contentId 콘텐츠 ID (TourAPI 식별자)
 * @param contentTypeId 콘텐츠 타입 (12=관광지·14=문화시설·15=축제·28=레포츠·32=숙박·39=음식점 등)
 * @param lclsSystm1 분류체계 대분류 코드 (NA·HS·AC·EX·FD 등) — 무드/카테고리 매핑 근거(없으면 null)
 * @param title 이름
 * @param address 주소
 * @param lat 위도 (mapy)
 * @param lng 경도 (mapx)
 * @param firstImage 대표 이미지 URL (없으면 null)
 * @param tel 전화 (없으면 null). 목록 응답에는 거의 안 온다 — 실측 0.7%(44/5,928)
 * @param lclsSystm2 분류체계 중분류 코드 (AC05 캠핑·VE05 복합관광시설 등). <b>대분류만으로 안 갈리는 것</b>이
 *     있어 함께 든다 — 실측(89곳 전수)에서 리조트가 대분류 {@code VE}(문화관광)로 오는데 실제로는 잘 곳이다
 */
public record TourPoi(
        String contentId,
        Integer contentTypeId,
        String lclsSystm1,
        String title,
        String address,
        Double lat,
        Double lng,
        String firstImage,
        String tel,
        String lclsSystm2) {
}
