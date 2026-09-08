package com.offway.core.itinerary.domain;

/**
 * 숙박 후보를 고르는 순서(#510) — <b>앞선 등급부터 채우고, 모자랄 때만 다음으로 내려간다</b>.
 *
 * <h2>왜 거리만으로는 안 되나</h2>
 *
 * <p>숙박 슬롯은 원래 볼거리 중심에서 <b>가장 가까운 것</b>으로 뽑았다. 후보가 관광 API 숙소뿐일 때는
 * 그것으로 충분했는데, 야영장을 후보에 더하자 결과가 뒤집혔다.
 *
 * <p>실측(2026-09-08 · 운영 DB, 83곳 × 2박)이 그 대가를 보여준다.
 *
 * <pre>
 *   거리만 본다        야영장이 숙박 자리의 42%(71/166) · 두 밤 다 캠핑인 지역 20곳
 *                     사진 있는 숙소가 141 → 119 로 **줄었다**
 *   이 순서를 쓴다     야영장 1%(2자리) · 두 밤 다 캠핑 0곳 · 사진 있는 숙소 166/166
 * </pre>
 *
 * <p>야영장은 산·계곡·유적 근처에 있어 볼거리 중심에 가깝다. 그래서 거리만 보면 <b>사진 있는 호텔을
 * 사진 없는 야영장이 밀어낸다</b> — 야영장을 들여온 이유(사진 있는 숙박을 늘리는 것)와 정반대다.
 *
 * <h2>두 축을 쓴다</h2>
 *
 * <p>첫째는 <b>사진</b>이다. 사진 없는 숙소는 코스에 올라가도 화면이 회색 판이 된다. 둘째는
 * <b>야영장인가</b> 다 — 같은 조건이면 잘 곳으로 지은 숙소가 먼저다. 야영장은 잘 수 있는 곳이지만
 * 모두가 텐트에서 자고 싶어 하지는 않는다.
 *
 * <p>그래서 야영장은 <b>숙소가 정말 없는 지역</b>에만 들어간다(실측 2자리). 후보 풀을 두껍게 하는 것이
 * 그 자체로 값어치이고, 코스에 항상 올라야 하는 것은 아니다.
 *
 * <p><b>trip 도메인을 모른다.</b> "야영장인가" 는 장소 식별자의 출처가 답하는데({@code PlaceOrigin}),
 * 그것을 여기서 알면 itinerary 가 trip 의 내부 표기에 묶인다. 판정 결과만 boolean 으로 받는다.
 */
public enum StayPreference {

    /** 사진 있는 숙소 — 카드가 서고, 잘 곳으로 지은 곳이다. */
    LODGING_WITH_PHOTO(false, true),

    /** 사진 있는 야영장 — 카드는 서지만 텐트다. 숙소가 모자랄 때 여기부터 채운다. */
    CAMPING_WITH_PHOTO(true, true),

    /** 사진 없는 숙소 — 화면은 비지만 잘 곳이다. */
    LODGING_WITHOUT_PHOTO(false, false),

    /** 사진 없는 야영장 — 마지막 수단. 여기까지 왔다면 그 지역에 후보가 거의 없다는 뜻이다. */
    CAMPING_WITHOUT_PHOTO(true, false);

    private final boolean camping;
    private final boolean photo;

    StayPreference(boolean camping, boolean photo) {
        this.camping = camping;
        this.photo = photo;
    }

    /**
     * 이 등급에 드는 후보인가.
     *
     * @param camping 야영장인가 — 호출자가 장소 식별자의 출처로 판정한다
     * @param hasPhoto 카드에 실을 사진이 있는가
     */
    public boolean covers(boolean camping, boolean hasPhoto) {
        return this.camping == camping && this.photo == hasPhoto;
    }
}
