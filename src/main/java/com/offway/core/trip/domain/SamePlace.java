package com.offway.core.trip.domain;

import com.offway.core.common.geo.Coordinate;
import java.util.Locale;

/**
 * 두 후보가 같은 장소인가 — <b>상호에 거리를 함께 본다</b>(#548).
 *
 * <h2>왜 필요했나</h2>
 *
 * <p>같은 장소가 한 코스에 두 번 들어갔다. 89곳 전수 실측(2026-09-09)에서 세 곳이 걸렸고, 횡성은 이렇게
 * 나왔다.
 *
 * <pre>
 * 1일차 SIGHT 횡성문화원  LIC-310273  (37.48655, 127.98393)   인허가
 * 1일차 SIGHT 횡성문화원  129886      (37.49147, 127.97850)   관광 API
 * </pre>
 *
 * <p>같은 곳인데 출처가 둘이라 식별자도 좌표도 다르다. 두 좌표는 <b>727m</b> 어긋나 있다.
 *
 * <h2>거리 기준을 출처에 따라 가르는 이유</h2>
 *
 * <p>같은 지역·같은 상호인 쌍의 거리 분포가 <b>출처 조합에 따라 완전히 다르다</b>(운영 실측).
 *
 * <table border="1">
 *   <caption>같은 이름 쌍의 거리 분포</caption>
 *   <tr><th>조합</th><th>쌍</th><th>100m 안</th><th>3㎞ 이상</th></tr>
 *   <tr><td>관광 API ↔ 인허가</td><td>1,651</td><td><b>88%</b></td><td>1.8%</td></tr>
 *   <tr><td>인허가 ↔ 인허가</td><td>2,864</td><td>39%</td><td><b>40%</b></td></tr>
 * </table>
 *
 * <p><b>출처가 다르면</b> 같은 곳을 두 기관이 각자 기록한 것이라 좌표가 벌어지는 것이 자연스럽다. 실제로
 * 300m~1㎞ 구간을 훑으면 보흥사·군위문화원·대가야생활촌·고택들처럼 <b>고유명사 시설</b>뿐이다.
 *
 * <p><b>출처가 같으면</b> 이야기가 다르다. 인허가끼리 이름이 겹치는 쌍은 <b>40%가 3㎞ 이상</b> 떨어져 있다
 * — 금강식당·일직식당·시골식당처럼 흔한 상호의 <b>다른 지점</b>이다. 500m~1㎞ 구간도 애매해서(홍천에서
 * 679m 가 다섯 번 반복된다 — 지번 중심으로 뭉갠 좌표로 보인다) 좁게 잡는다.
 *
 * <p>그래서 <b>출처가 다르면 {@value #ACROSS_SOURCES_KM}㎞, 같으면 {@value #WITHIN_SOURCE_KM}㎞</b> 다.
 * 앞의 값은 위 실측에서 오탐이 안 보이는 선까지 넓힌 것이고, 뒤의 값은 예전 100m 격자와 비슷하되 격자
 * 경계 문제를 없앤 값이다 — 격자는 실제로 50m 떨어져 있어도 경계를 사이에 두면 다른 곳으로 봤다.
 *
 * <h2>이름은 정확히 맞아야 한다</h2>
 *
 * <p>띄어쓰기·대소문자만 지운다. 그보다 느슨하게 풀면(부분 일치·유사도) 오탐이 빠르게 는다 — 위 실측이
 * 근거로 삼은 것도 정확 일치라, 여기서 규칙을 바꾸면 그 숫자가 더는 이 코드의 근거가 아니다.
 */
public final class SamePlace {

    /** 출처가 다를 때의 상한(㎞). 관광 API 와 인허가가 같은 곳을 다른 좌표로 적는 폭이다. */
    private static final double ACROSS_SOURCES_KM = 1.0;

    /** 출처가 같을 때의 상한(㎞). 같은 상호의 다른 지점을 접지 않으려고 좁게 둔다. */
    private static final double WITHIN_SOURCE_KM = 0.2;

    private SamePlace() {
    }

    /**
     * 같은 장소로 볼 것인가.
     *
     * <p><b>이름을 먼저 본다.</b> 대부분의 쌍은 여기서 갈리고, 그러면 거리를 아예 안 잰다 — 풀 하나가
     * 100건 남짓이라 짝을 전부 견주는데, 그 대부분이 문자열 비교 한 번으로 끝난다.
     *
     * @return 상호가 (띄어쓰기·대소문자를 무시하고) 같고 거리가 상한 안이면 참
     */
    public static boolean is(
            String titleA, String idA, double latA, double lngA,
            String titleB, String idB, double latB, double lngB) {
        if (!sameTitle(titleA, titleB)) {
            return false;
        }
        if (!isFinite(latA, lngA) || !isFinite(latB, lngB)) {
            // 좌표를 모르면 견줄 수 없다. 이름만으로 접으면 "○○식당" 본점과 2호점이 한 곳이 된다.
            return false;
        }
        return new Coordinate(latA, lngA).haversineKmTo(new Coordinate(latB, lngB)) <= limitKm(idA, idB);
    }

    /** {@link Coordinate} 가 거절할 값인가 — 생성자에서 던지게 두면 후보 하나가 풀 전체를 막는다. */
    private static boolean isFinite(double lat, double lng) {
        return Double.isFinite(lat) && Double.isFinite(lng)
                && Math.abs(lat) <= 90 && Math.abs(lng) <= 180;
    }

    private static boolean sameTitle(String a, String b) {
        String left = normalize(a);
        return !left.isEmpty() && left.equals(normalize(b));
    }

    /**
     * 소스마다 띄어쓰기·대소문자가 달라 그대로 비교하면 같은 곳을 다른 곳으로 본다.
     *
     * <p><b>{@code Locale.ROOT} 를 명시한다.</b> 인자 없는 {@code toLowerCase()} 는 JVM 기본 로케일을
     * 따르는데, 그건 우리가 고르는 값이 아니라 <b>서버가 어디서 뜨는지</b>에 달렸다. 터키어 로케일에서는
     * {@code "I"} 가 점 없는 {@code "ı"} 로 내려가, 같은 이름이 환경에 따라 다르게 정규화된다. 장소 이름
     * 비교는 로케일과 무관해야 한다.
     */
    private static String normalize(String title) {
        return title == null ? "" : title.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    /** 출처가 갈리면 넓게, 같으면 좁게 — 근거는 이 클래스 문서의 표. */
    private static double limitKm(String idA, String idB) {
        return PlaceOrigin.of(idA) == PlaceOrigin.of(idB) ? WITHIN_SOURCE_KM : ACROSS_SOURCES_KM;
    }
}
