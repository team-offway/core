package com.offway.core.transport.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CoordinateTest {

    @Test
    void 유효한_위경도로_생성된다() {
        Coordinate seoul = new Coordinate(37.5665, 126.9780);
        assertEquals(37.5665, seoul.lat());
        assertEquals(126.9780, seoul.lng());
    }

    @ParameterizedTest
    @CsvSource({
        "90.1, 127.0",
        "-90.1, 127.0",
        "37.5, 180.1",
        "37.5, -180.1"
    })
    void 위경도_범위를_벗어나면_불변식_위반이다(double lat, double lng) {
        assertThrows(IllegalArgumentException.class, () -> new Coordinate(lat, lng));
    }

    @ParameterizedTest
    @CsvSource({"NaN, 127.0", "37.5, NaN", "Infinity, 127.0", "37.5, -Infinity"})
    void 비유한_좌표는_불변식_위반이다(double lat, double lng) {
        assertThrows(IllegalArgumentException.class, () -> new Coordinate(lat, lng));
    }

    @Test
    void 같은_좌표까지의_거리는_0이다() {
        Coordinate seoul = new Coordinate(37.5665, 126.9780);
        assertEquals(0.0, seoul.haversineKmTo(seoul), 0.0001);
    }

    @Test
    void 서울_부산_대권거리가_약_325km다() {
        Coordinate seoul = new Coordinate(37.5665, 126.9780);
        Coordinate busan = new Coordinate(35.1796, 129.0756);
        // 실제 대권거리 ≈ 325㎞. haversine 공식·지구반지름 상수의 회귀를 잠근다.
        assertEquals(325.0, seoul.haversineKmTo(busan), 8.0);
    }

    @Test
    void 거리는_대칭이다() {
        Coordinate a = new Coordinate(37.5665, 126.9780);
        Coordinate b = new Coordinate(35.1796, 129.0756);
        assertEquals(a.haversineKmTo(b), b.haversineKmTo(a), 0.0001);
    }
}
