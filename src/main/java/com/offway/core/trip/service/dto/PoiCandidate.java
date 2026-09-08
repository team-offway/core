package com.offway.core.trip.service.dto;

import lombok.Builder;

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
 *
 * <p><b>빌더로 조립한다.</b> 열한 칸 중 여섯이 {@code String} 이고 그중 넷이 대개 {@code null} 이라,
 * 위치 생성자로는 두 칸을 맞바꿔도 컴파일이 통과한다 — 주소 자리에 캐치프레이즈가 들어가도 화면이
 * 이상해질 때까지 아무도 모른다.
 */
@Builder
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
        String lclsSystm2,
        /**
         * 음식 분류 코드 — 인허가는 {@code PlaceCategory} 이름({@code KOREAN}·{@code NOODLE}),
         * TourAPI 는 {@code cat3}({@code A05020100}) 이다(#음식중복).
         *
         * <p><b>두 출처의 코드를 한 칸에 담는다.</b> 갈라 두면 쓰는 쪽이 매번 어느 출처인지 물어야 하는데,
         * 판정은 {@link com.offway.core.trip.domain.FoodTaste} 한 곳이 소유하므로 거기서 둘 다 읽으면 된다.
         *
         * <p>없으면 null 이다 — 국가유산·축제처럼 음식이 아닌 출처가 그렇다.
         */
        String foodCategory) {
}
