package com.offway.core.weather.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 관광기후지수 매칭 키(#130). 우리 시드는 정식 시도명, 기상청 응답은 축약형이라 둘이 같은 키로 접혀야 한다.
 */
class SigunguKeyTest {

    @ParameterizedTest(name = "{0} 과 {1} 은 같은 키")
    @CsvSource({
            "충청북도, 충북, 단양군",
            "충청남도, 충남, 서천군",
            "전남광주통합특별시, 전남, 완도군",
            "경상북도, 경북, 영양군",
            "경상남도, 경남, 남해군",
            "강원특별자치도, 강원, 정선군",
            "강원도, 강원, 정선군",
            "전북특별자치도, 전북, 무주군",
            "전라북도, 전북, 무주군",
            "서울특별시, 서울, 종로구",
            "부산광역시, 부산, 동구",
            "제주특별자치도, 제주, 서귀포시",
    })
    void 정식명과_축약형이_같은_키가_된다(String full, String shortName, String sigungu) {
        // 앞 두 글자를 자르는 방식으로는 갈라진다 — '충청북도' 를 자르면 '충청' 이라 '충북' 과 안 맞는다.
        assertEquals(SigunguKey.of(shortName, sigungu), SigunguKey.of(full, sigungu));
    }

    @Test
    void 시도가_다르면_같은_시군구명도_다른_키다() {
        // 광역시 구(서구·동구·남구)와 고성군이 여러 시도에 있다. 이름만 보면 엉뚱한 지역의 지수를 준다.
        assertNotEquals(SigunguKey.of("부산광역시", "동구"), SigunguKey.of("대구광역시", "동구"));
        assertNotEquals(SigunguKey.of("강원특별자치도", "고성군"), SigunguKey.of("경상남도", "고성군"));
    }

    @Test
    void 시도나_시군구가_비면_맞출_수_없다() {
        assertNull(SigunguKey.of(null, "정선군"));
        assertNull(SigunguKey.of("강원특별자치도", null));
        assertNull(SigunguKey.of("  ", "정선군"));
    }

    @Test
    void 앞뒤_공백은_무시한다() {
        assertEquals(SigunguKey.of("강원", "정선군"), SigunguKey.of(" 강원특별자치도 ", " 정선군 "));
    }
}
