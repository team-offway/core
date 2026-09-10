package com.offway.core.common.geo;

/**
 * 우리가 다루는 땅 — <b>이 밖의 좌표는 장소로 받지 않는다</b>(#547).
 *
 * <h2>왜 필요했나</h2>
 *
 * <p>관광 API 가 <b>남중국해 좌표</b>를 준다. 담양호가 {@code (19.69, 117.99)} 로 왔고, 그 좌표가 코스에
 * 그대로 실려 이동시간이 <b>2,918분(48.6시간)</b> 으로 나갔다. 지도 핀은 필리핀 서쪽 바다에 찍히고, 다음
 * 칸도 거기서 돌아오는 것으로 계산돼 그 뒤 동선이 통째로 틀어진다.
 *
 * <p>{@code region_poi} 6,218건 중 10건이고, 그중 아홉이 <b>토씨 하나 안 틀리고 같은 좌표</b>
 * {@code (19.69442748, 117.9925662504)} 다. 흩어진 실수가 아니라 센티널이거나 파싱 오류라는 뜻이다.
 *
 * <p>{@link Coordinate} 가 이미 검증하지만 그쪽은 <b>지구 위의 점인가</b>만 본다. 남중국해도 지구 위다.
 *
 * <h2>범위를 이렇게 잡은 근거</h2>
 *
 * <p>우리 데이터의 실제 분포에서 왔다(2026-09-09 운영 기준).
 *
 * <table border="1">
 *   <caption>출처별 좌표 범위</caption>
 *   <tr><th>출처</th><th>건수</th><th>위도</th><th>경도</th></tr>
 *   <tr><td>인허가</td><td>121,357</td><td>33.99 ~ 38.59</td><td>124.62 ~ 130.91</td></tr>
 *   <tr><td>국가유산</td><td>3,437</td><td>34.07 ~ 38.40</td><td>124.62 ~ <b>131.87</b>(독도)</td></tr>
 *   <tr><td>야영장</td><td>1,698</td><td><b>33.49</b> ~ 38.54</td><td>125.95 ~ 130.83</td></tr>
 *   <tr><td>인구감소지역 89곳</td><td>89</td><td>34.31 ~ 38.38</td><td>126.26 ~ 130.91</td></tr>
 * </table>
 *
 * <p>가장자리를 정하는 것은 <b>섬</b>이다 — 서쪽은 백령도, 동쪽은 독도, 남쪽은 제주 남단. 거기에 여유를
 * 얹었다. 이 범위로 전 출처를 훑으면 <b>정상 데이터는 한 건도 안 걸리고</b> 오염 10건만 걸린다
 * (인허가 0 · 국가유산 0 · 야영장 0 · 관광 API 10).
 *
 * <p><b>지역 중심에서의 거리로 재지 않는 이유</b>는 섬이 흩어진 지역이다. 옹진·신안처럼 지역 경계가 수십
 * ㎞ 에 걸친 곳에서는 멀쩡한 장소가 중심에서 한참 떨어져 있어, 거리로 자르면 그런 지역만 얇아진다. 절대
 * 범위는 그 편차를 만들지 않는다.
 */
public final class ServiceArea {

    /** 제주 남단(마라도 33.06)보다 아래. 야영장 최소값이 33.49 다. */
    private static final double MIN_LAT = 32.5;

    /** 남한 최북단(강원 고성 38.61)보다 위. 데이터 최대값이 38.59 다. */
    private static final double MAX_LAT = 39.0;

    /** 서해 최서단(백령도 124.61)보다 서쪽. 데이터 최소값이 124.62 다. */
    private static final double MIN_LNG = 124.0;

    /** 동해 최동단(독도 131.87)보다 동쪽. 국가유산 최대값이 그 독도다. */
    private static final double MAX_LNG = 132.5;

    private ServiceArea() {
    }

    /**
     * 이 좌표를 장소로 받아도 되는가.
     *
     * <p>{@code (0, 0)} 도 여기서 걸린다 — 기니만 바다이고, 좌표를 못 받은 행이 그 값으로 오는 경우가 있다.
     *
     * @param lat 위도(모르면 {@code null})
     * @param lng 경도(모르면 {@code null})
     * @return 둘 다 있고 범위 안이면 참. <b>하나라도 없으면 거짓</b> — 좌표 없는 장소는 동선에 못 올린다
     */
    public static boolean contains(Double lat, Double lng) {
        if (lat == null || lng == null) {
            return false;
        }
        return Double.isFinite(lat) && Double.isFinite(lng)
                && lat >= MIN_LAT && lat <= MAX_LAT
                && lng >= MIN_LNG && lng <= MAX_LNG;
    }
}
