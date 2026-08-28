package com.offway.core.weather.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 중기예보 구역 판정(#129). 시도명 하나로 갈리는 단순 매핑처럼 보이지만 강원이 예외라 분기가 있다.
 */
class MidLandRegionTest {

    @ParameterizedTest(name = "{0} {1} → {2}")
    @CsvSource({
            "서울특별시, 종로구, 11B00000",
            "경기도, 가평군, 11B00000",
            "충청북도, 단양군, 11C10000",
            "충청남도, 서천군, 11C20000",
            "세종특별자치시, 세종시, 11C20000",
            "전북특별자치도, 무주군, 11F10000",
            "전남광주통합특별시, 완도군, 11F20000",
            "광주광역시, 동구, 11F20000",
            "경상북도, 영양군, 11H10000",
            "경상남도, 남해군, 11H20000",
            "부산광역시, 동구, 11H20000",
            "제주특별자치도, 서귀포시, 11G00000",
    })
    void 시도로_구역을_찾는다(String sido, String sigungu, String regId) {
        assertEquals(regId, MidLandRegion.of(sido, sigungu).orElseThrow().regId());
    }

    @ParameterizedTest(name = "강원 {0} 은 영동")
    @ValueSource(strings = {"강릉시", "동해시", "속초시", "삼척시", "태백시", "고성군", "양양군"})
    void 강원_영동_시군은_영서와_다른_예보를_본다(String sigungu) {
        // 백두대간 동쪽은 눈·바람이 영서와 딴판이다. 시도명만 보면 전부 영서로 떨어진다.
        assertEquals(MidLandRegion.GANGWON_YEONGDONG,
                MidLandRegion.of("강원특별자치도", sigungu).orElseThrow());
    }

    @ParameterizedTest(name = "강원 {0} 은 영서")
    @ValueSource(strings = {"정선군", "영월군", "평창군", "홍천군", "횡성군", "철원군", "화천군", "양구군"})
    void 강원_나머지는_영서다(String sigungu) {
        assertEquals(MidLandRegion.GANGWON_YEONGSEO,
                MidLandRegion.of("강원특별자치도", sigungu).orElseThrow());
    }

    @Test
    void 특별자치도_전환_전후_시도명을_모두_받는다() {
        // region 시드가 '강원도'·'전라북도' 로 남아 있어도 조회가 죽지 않아야 한다.
        assertEquals(MidLandRegion.GANGWON_YEONGSEO, MidLandRegion.of("강원도", "정선군").orElseThrow());
        assertEquals(MidLandRegion.GANGWON_YEONGDONG, MidLandRegion.of("강원도", "태백시").orElseThrow());
        assertEquals(MidLandRegion.JEONBUK, MidLandRegion.of("전라북도", "무주군").orElseThrow());
    }

    @Test
    void 모르는_시도는_빈_값이다() {
        // 조용히 엉뚱한 구역을 주면 틀린 날씨가 정상처럼 나간다. 호출자가 degrade 를 로그로 남기게 비운다.
        assertTrue(MidLandRegion.of("경기도특별시", "없는군").isEmpty());
        assertTrue(MidLandRegion.of(null, null).isEmpty());
    }

    @Test
    void 구역_코드는_서로_겹치지_않는다() {
        // 상수를 늘리다 코드를 복사해 붙이면 두 구역이 같은 예보를 보게 된다.
        long distinct = Arrays.stream(MidLandRegion.values()).map(MidLandRegion::regId).distinct().count();

        assertEquals(MidLandRegion.values().length, distinct,
                "중복된 regId: " + Arrays.stream(MidLandRegion.values())
                        .map(MidLandRegion::regId).collect(Collectors.joining(", ")));
    }
}
