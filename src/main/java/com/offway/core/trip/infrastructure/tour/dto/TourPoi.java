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
        String lclsSystm2,
        /**
         * 음식 소분류({@code cat3}) — {@code A05020100}(한식)·{@code A05020400}(중식) 등.
         *
         * <p>목록 응답에 <b>이미 실려 온다</b>. 같은 끼니를 두 번 넣지 않으려면 분류가 필요한데,
         * 이걸 안 읽고 있었다 — 호출은 안 늘고 파싱만 는다.
         */
        String cat3) {

    /**
     * {@code cat3} 이전의 형태 — 이 값을 안 쓰는 자리(테스트 픽스처·다른 조회)가 그대로 남게 한다.
     *
     * <p>선택값이라 없어도 판정이 성립한다. {@link com.offway.core.trip.domain.FoodTaste} 가 상호를
     * 먼저 보고, 분류는 못 읽었을 때만 쓴다.
     */
    public TourPoi(String contentId, Integer contentTypeId, String lclsSystm1, String title, String address,
            Double lat, Double lng, String firstImage, String tel, String lclsSystm2) {
        this(contentId, contentTypeId, lclsSystm1, title, address, lat, lng, firstImage, tel, lclsSystm2, null);
    }
}
