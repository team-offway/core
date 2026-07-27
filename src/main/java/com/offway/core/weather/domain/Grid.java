package com.offway.core.weather.domain;

/**
 * 기상청 단기예보 격자 좌표(nx·ny) — 위경도를 기상청 Lambert Conformal Conic(DFS) 격자로 변환한다. 동네예보는 위경도가
 * 아니라 이 격자로 조회한다.
 *
 * <p>변환식·상수는 기상청 배포 공식 그대로다(순수 계산이라 단위 테스트로 검증). 예: 서울시청(37.5665, 126.9780) → (60, 127).
 *
 * @param nx 격자 X
 * @param ny 격자 Y
 */
public record Grid(int nx, int ny) {

    private static final double RE = 6371.00877; // 지구 반경(㎞)
    private static final double GRID = 5.0; // 격자 간격(㎞)
    private static final double SLAT1 = 30.0; // 표준 위도 1
    private static final double SLAT2 = 60.0; // 표준 위도 2
    private static final double OLON = 126.0; // 기준점 경도
    private static final double OLAT = 38.0; // 기준점 위도
    private static final double XO = 43; // 기준점 X
    private static final double YO = 136; // 기준점 Y
    private static final double DEGRAD = Math.PI / 180.0;

    public static Grid from(double lat, double lng) {
        double re = RE / GRID;
        double slat1 = SLAT1 * DEGRAD;
        double slat2 = SLAT2 * DEGRAD;
        double olon = OLON * DEGRAD;
        double olat = OLAT * DEGRAD;

        double sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);
        double sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sf = Math.pow(sf, sn) * Math.cos(slat1) / sn;
        double ro = Math.tan(Math.PI * 0.25 + olat * 0.5);
        ro = re * sf / Math.pow(ro, sn);

        double ra = Math.tan(Math.PI * 0.25 + lat * DEGRAD * 0.5);
        ra = re * sf / Math.pow(ra, sn);
        double theta = lng * DEGRAD - olon;
        if (theta > Math.PI) {
            theta -= 2.0 * Math.PI;
        }
        if (theta < -Math.PI) {
            theta += 2.0 * Math.PI;
        }
        theta *= sn;

        int nx = (int) Math.floor(ra * Math.sin(theta) + XO + 0.5);
        int ny = (int) Math.floor(ro - ra * Math.cos(theta) + YO + 0.5);
        return new Grid(nx, ny);
    }
}
