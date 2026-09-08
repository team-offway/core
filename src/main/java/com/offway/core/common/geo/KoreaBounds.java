package com.offway.core.common.geo;

/**
 * 대한민국 육지·부속도서의 좌표 범위 — <b>출처가 잘못 적은 행을 걸러내는 자</b>.
 *
 * <p>공공데이터는 좌표를 0 으로 두거나 위경도를 바꿔 적은 행이 섞여 온다. 그대로 담으면 동선이 바다
 * 한가운데를 지나거나 지도가 엉뚱한 곳을 연다.
 *
 * <p><b>{@link Coordinate} 와 다른 질문에 답한다.</b> 그쪽은 "위경도로서 말이 되는 값인가"(-90~90)를 보고,
 * 여기는 "우리 서비스가 다루는 땅 안인가" 를 본다. 전자는 값의 문법이고 후자는 도메인 제약이다.
 *
 * <p>같은 숫자가 {@code HeritagePlace}·{@code FestivalPlace} 에 각각 박혀 있다. 세 번째 사본을 만들지
 * 않으려고 여기로 뽑았고, 그 둘을 옮기는 것은 이 작업(#510)의 범위 밖이라 남겨 뒀다.
 */
public final class KoreaBounds {

    private static final double MIN_LAT = 33.0;
    private static final double MAX_LAT = 39.0;
    private static final double MIN_LNG = 124.0;
    private static final double MAX_LNG = 132.0;

    private KoreaBounds() {
    }

    /**
     * 이 좌표를 담을 수 있는가 — <b>어댑터가 먼저 물어보는 자리</b>.
     *
     * <p>판정을 공개하는 이유는 도메인 생성자에서 터지는 것을 막기 위해서다. 출처가 좌표를 0 으로 올린
     * 행 하나가 예외를 던지면 <b>그 회차 적재가 통째로 실패한다</b> — 그 한 건만 건너뛰면 될 일이다.
     */
    public static boolean contains(double lat, double lng) {
        return Double.isFinite(lat) && lat >= MIN_LAT && lat <= MAX_LAT
                && Double.isFinite(lng) && lng >= MIN_LNG && lng <= MAX_LNG;
    }

    /** 불변식 강제 — 도메인 생성자가 쓴다. 어댑터가 이미 걸렀다면 여기 닿지 않는다. */
    public static void require(double lat, double lng) {
        if (!Double.isFinite(lat) || lat < MIN_LAT || lat > MAX_LAT) {
            throw new IllegalArgumentException("위도가 대한민국 범위를 벗어났습니다: " + lat);
        }
        if (!Double.isFinite(lng) || lng < MIN_LNG || lng > MAX_LNG) {
            throw new IllegalArgumentException("경도가 대한민국 범위를 벗어났습니다: " + lng);
        }
    }
}
