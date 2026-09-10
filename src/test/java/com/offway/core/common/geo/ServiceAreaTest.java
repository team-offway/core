package com.offway.core.common.geo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 우리 땅 밖의 좌표를 장소로 받지 않는가(#547).
 *
 * <p>여기서 틀리면 두 방향으로 나빠진다 — 느슨하면 남중국해 좌표가 코스에 들어가 이동시간이 48시간으로
 * 나가고, 빡빡하면 멀쩡한 섬(백령도·독도·마라도)의 장소를 우리가 지운다.
 */
class ServiceAreaTest {

    @ParameterizedTest(name = "{2} 는 우리 땅이다")
    @CsvSource({
        "37.5665, 126.9780, 서울",
        "33.4996, 126.5312, 제주",
        "38.3803, 128.4677, 강원_고성",
        "34.3112, 126.7550, 완도",
        "35.3152, 126.9838, 담양",
    })
    void 우리가_다루는_지역은_전부_통과한다(double lat, double lng, String where) {
        assertTrue(ServiceArea.contains(lat, lng), where);
    }

    @ParameterizedTest(name = "{2} 는 가장자리지만 우리 땅이다")
    @CsvSource({
        // 가장자리를 정하는 것은 섬이다. 여기가 빡빡하면 그 섬의 장소를 통째로 잃는다.
        "37.9642, 124.6220, 백령도_서쪽끝",
        "37.2411, 131.8694, 독도_동쪽끝",
        "33.0600, 126.2670, 마라도_남쪽끝",
        "38.5864, 128.3556, 최북단_고성",
    })
    void 실제_데이터의_가장자리도_통과한다(double lat, double lng, String where) {
        assertTrue(ServiceArea.contains(lat, lng), where);
    }

    @Test
    void 남중국해_좌표를_거절한다() {
        // 이 값이 이 클래스의 이유다 — region_poi 오염 10건 중 아홉이 토씨 하나 안 틀리고 이 좌표였다.
        assertFalse(ServiceArea.contains(19.69442748, 117.9925662504));
    }

    @Test
    void 영_영_좌표를_거절한다() {
        // 기니만 바다다. 좌표를 못 받은 행이 이 값으로 오는 경우가 있다(보령목재문화체험장).
        assertFalse(ServiceArea.contains(0.0, 0.0));
    }

    @ParameterizedTest(name = "{2} 는 밖이다")
    @CsvSource({
        "35.6762, 139.6503, 도쿄",
        "39.9042, 116.4074, 베이징",
        "-33.8688, 151.2093, 시드니",
        "43.0000, 128.0000, 위도가_위로_벗어남",
        "35.0000, 133.0000, 경도가_동쪽으로_벗어남",
    })
    void 우리_땅_밖은_거절한다(double lat, double lng, String where) {
        assertFalse(ServiceArea.contains(lat, lng), where);
    }

    @Test
    void 좌표가_없으면_거절한다() {
        // 좌표 없는 장소는 동선에 못 올린다 — "모른다" 를 통과시키면 그 판단이 뒤로 미뤄진다.
        assertFalse(ServiceArea.contains(null, 126.9780));
        assertFalse(ServiceArea.contains(37.5665, null));
        assertFalse(ServiceArea.contains(null, null));
    }

    @Test
    void 숫자가_아닌_값을_거절한다() {
        // NaN 은 어떤 비교에도 거짓이라 부등호만으로는 안 걸린다. 거리 계산까지 흘러가면 전부 오염된다.
        assertFalse(ServiceArea.contains(Double.NaN, 126.9780));
        assertFalse(ServiceArea.contains(37.5665, Double.NaN));
        assertFalse(ServiceArea.contains(Double.POSITIVE_INFINITY, 126.9780));
    }
}
