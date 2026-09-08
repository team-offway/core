package com.offway.core.itinerary.domain;

/**
 * 카페를 어떤 순서로 고를지 정하는 등급(#527) — <b>거리가 아니라 순위가 먼저다</b>.
 *
 * <h2>왜 필요했나</h2>
 *
 * <p>예전에는 거리 하나로만 골랐다. 그런데 카페 후보의 대부분이 <b>사진 없는 인허가 장소</b>라, 거리로
 * 고르면 그쪽이 이긴다. 실측(2026-09-08)에서 지역당 TourAPI 카페는 0~10곳인데 인허가 카페는 상한인
 * 100곳이 들어온다 — 풀의 91~98%가 사진 없는 쪽이다. 완도는 사진 있는 카페가 아예 0이었다.
 *
 * <h2>순위를 어디서 얻나</h2>
 *
 * <p>연관 관광지의 {@code 음식} 분류에 카페가 들어 있다 — "그 관광지에 간 사람이 실제로 들르는 카페" 다.
 * 인허가 기준으로 COFFEE 757 · BAKERY 150 · DESSERT 7 · TRADITIONAL_TEA 6 이고, 89곳 중 <b>85곳</b>에
 * 있다(지역당 평균 4곳, 56곳은 3곳 이상). 2박3일이 3곳을 쓰니 대부분 지역은 이 등급에서 끝난다.
 *
 * <p><b>중심관광지 인기순은 카페에 못 쓴다.</b> 그 데이터의 분류는 관광지 7종과 숙박뿐이고 음식·카페가
 * 아예 없다. 카페의 순위 신호는 연관 관광지가 유일하다.
 *
 * <h2>사진보다 순위를 앞에 두는 이유</h2>
 *
 * <p>연관 카페는 전부 인허가 장소라 사진이 없다({@code LicensedPlace} 에 이미지 컬럼 자체가 없다).
 * 그러니 이 순서는 <b>"사진 있는 카페"와 "사람들이 실제로 가는 카페" 중 무엇을 먼저 보여줄까</b>를
 * 정하는 것이다. 아무 근거 없이 가까운 곳보다는, 근거가 있는 곳이 낫다고 봤다.
 *
 * <p>둘은 겹치지 않는다 — 연관은 {@code LIC-} 식별자고 사진은 TourAPI 콘텐츠에만 붙어서, 한 후보가
 * 두 등급에 걸릴 일이 없다. 그래서 순서가 곧 우선순위다.
 */
public enum CafePreference {

    /** 함께 가는 카페 — 연관 순위대로. */
    RELATED(true, false),

    /** 사진 있는 카페 — 연관에 없을 때. 카드가 비지 않는다. */
    PHOTO(false, true),

    /** 나머지 — 근거가 없으니 가까운 곳으로. */
    REST(false, false);

    /**
     * 순위를 따르되 <b>여기까지만</b>(㎞) — 권역 밖은 아무리 순위가 높아도 건너뛴다.
     *
     * <p>순위는 "그 관광지 가는 사람이 들르는 곳" 이지 "우리 코스에서 갈 만한 곳" 이 아니다. 섬이
     * 흩어진 지역에서는 1순위가 볼거리 중심에서 수십 ㎞ 밖에 있다 — 실측에서 카페 3곳 합산 이동이
     * <b>최대 226㎞</b> 였다.
     *
     * <p>30㎞ 로 정한 근거는 <b>공짜라서</b>다. 실측(85곳):
     *
     * <table border="1">
     *   <caption>상한별 비용</caption>
     *   <tr><th>상한</th><th>연관 카페를 잃는 지역</th><th>3곳 다 채움</th><th>합산 중앙값</th><th>최대</th></tr>
     *   <tr><td>없음</td><td>0</td><td>85곳</td><td>19.7㎞</td><td>226.2㎞</td></tr>
     *   <tr><td><b>30㎞</b></td><td><b>0</b></td><td><b>85곳</b></td><td>19.3㎞</td><td><b>73.0㎞</b></td></tr>
     *   <tr><td>20㎞</td><td>1</td><td>81곳</td><td>18.3㎞</td><td>42.3㎞</td></tr>
     *   <tr><td>15㎞</td><td>4</td><td>75곳</td><td>15.3㎞</td><td>34.1㎞</td></tr>
     * </table>
     *
     * <p>더 좁히면 순위를 실제로 버리기 시작한다(20㎞ 부터 지역이 깎인다). 30㎞ 는 <b>한 곳도 안 잃고</b>
     * 최악만 3분의 1로 줄인다. 같은 값이 {@code BusTerminalResolver} 의 권역 반경이기도 하다(#107) —
     * 이 레포에서 30㎞ 는 이미 "그 지역 권역" 을 뜻한다.
     *
     * <p><b>순위가 비슷할 때 가까운 쪽을 고르는 것(밴드)은 넣지 않았다.</b> 실측에서 밴드는 <b>중앙값
     * 0.0㎞</b> 였다 — 순위와 거리가 그 정도 간격에서는 상관이 없어, 56곳 중 2곳만 1㎞ 넘게 줄었다.
     * 거리는 밴드가 아니라 <b>날짜 배정</b>에서 값을 한다(그날 볼거리에 가까운 카페를 준다).
     */
    public static final double DETOUR_LIMIT_KM = 30.0;

    private final boolean related;
    private final boolean photo;

    CafePreference(boolean related, boolean photo) {
        this.related = related;
        this.photo = photo;
    }

    /**
     * 이 후보가 이 등급인가.
     *
     * <p>연관 순위가 있으면 사진 유무를 <b>보지 않는다</b> — 순위가 더 강한 근거라 거기서 갈리면 안 된다.
     */
    public boolean covers(boolean hasRank, boolean hasPhoto) {
        if (related) {
            return hasRank;
        }
        return !hasRank && this.photo == hasPhoto;
    }

    /**
     * 이 등급 안에서 순위로 정렬하나.
     *
     * <p>순위가 있는 등급만 참이다. 나머지는 견줄 근거가 없어 거리로 간다.
     */
    public boolean ordersByRank() {
        return related;
    }
}
