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
 * @param address 주소(없으면 null)
 * @param catchphrase 구석구석 캐치프레이즈(추천 한 줄, 없으면 null)
 */
public record PoiCandidate(
        String contentId, int contentTypeId, String title, double lat, double lng,
        String imageUrl, String address, String catchphrase, String tel,
        /**
         * TourAPI 분류체계 대분류({@code lclsSystm1}) — <b>어느 풀에 들어갈지를 이 값이 정한다</b>(#304).
         *
         * <p>예전에는 {@code contentTypeId} 로 갈랐는데 그 둘이 어긋난다. 실측(89곳 전수)에서
         * <b>야영장·캠핑장 625건이 {@code AC05}(숙박)인데 타입은 28(레포츠)</b> 로 왔다 — 그래서
         * 볼거리 풀에 들어가고 숙박 풀은 굶었다. 우리 DB 출처(인허가·국가유산)는 이 값이 없어 null 이다.
         */
        String lclsSystm1,
        /**
         * 분류체계 중분류({@code lclsSystm2}) — <b>대분류만으로 안 갈리는 것</b>을 가른다(#304).
         *
         * <p>실측(89곳 전수)에서 카라반·글램핑 리조트가 대분류 {@code VE}(문화관광)로 왔다. 문화관광이
         * 볼거리인 것은 맞지만 <b>리조트는 잘 곳</b>이라, 중분류 {@code VE05}(복합관광시설)를 봐야 갈린다.
         */
        String lclsSystm2) {
}
