package com.offway.core.transport.domain;

import java.util.Objects;

/**
 * 위경도 좌표(WGS84) 값객체. 두 좌표 사이 대권거리(haversine)를 스스로 계산한다.
 */
public record Coordinate(double lat, double lng) {

    private static final double MIN_LAT = -90.0;
    private static final double MAX_LAT = 90.0;
    private static final double MIN_LNG = -180.0;
    private static final double MAX_LNG = 180.0;

    /** 지구 평균 반지름(㎞). */
    private static final double EARTH_RADIUS_KM = 6371.0;

    public Coordinate {
        // 불변식 — 좌표 소스(region 시드·요청)가 유효함을 보장한다. NaN·무한대는 거리 계산을 오염시킨다.
        if (!Double.isFinite(lat) || lat < MIN_LAT || lat > MAX_LAT) {
            throw new IllegalArgumentException("위도가 범위를 벗어났습니다: " + lat);
        }
        if (!Double.isFinite(lng) || lng < MIN_LNG || lng > MAX_LNG) {
            throw new IllegalArgumentException("경도가 범위를 벗어났습니다: " + lng);
        }
    }

    /** 이 좌표에서 대상 좌표까지 대권거리(㎞). */
    public double haversineKmTo(Coordinate other) {
        Objects.requireNonNull(other, "other 는 null 일 수 없습니다.");
        double dLat = Math.toRadians(other.lat - lat);
        double dLng = Math.toRadians(other.lng - lng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(other.lat))
                        * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        // 부동소수점 오차로 a 가 1을 살짝 넘으면 sqrt(1-a) 가 NaN 이 된다(대척점 근처). [0,1] 로 clamp.
        double clamped = Math.min(1.0, Math.max(0.0, a));
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(clamped), Math.sqrt(1 - clamped));
    }
}
