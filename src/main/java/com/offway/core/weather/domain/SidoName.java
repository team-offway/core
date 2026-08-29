package com.offway.core.weather.domain;

import java.util.Map;

/**
 * 시도 명칭 매핑 — region 의 정식 시도명("강원특별자치도")을 축약 시도명("강원")으로 바꾼다.
 *
 * <p>이 축약형은 기상청 관광기후지수 응답의 {@code doName} 값이다(#130).
 * 실측으로 확인한 관광기후지수의 시도명 17종이 이 표의 값과 정확히 일치한다.
 */
public final class SidoName {

    private static final Map<String, String> SHORT_NAMES = Map.ofEntries(
            Map.entry("서울특별시", "서울"),
            Map.entry("부산광역시", "부산"),
            Map.entry("대구광역시", "대구"),
            Map.entry("인천광역시", "인천"),
            Map.entry("광주광역시", "광주"),
            Map.entry("대전광역시", "대전"),
            Map.entry("울산광역시", "울산"),
            Map.entry("세종특별자치시", "세종"),
            Map.entry("경기도", "경기"),
            Map.entry("강원도", "강원"),
            Map.entry("강원특별자치도", "강원"),
            Map.entry("충청북도", "충북"),
            Map.entry("충청남도", "충남"),
            Map.entry("전라북도", "전북"),
            Map.entry("전북특별자치도", "전북"),
            Map.entry("전라남도", "전남"),
            Map.entry("전남광주통합특별시", "전남"),
            Map.entry("경상북도", "경북"),
            Map.entry("경상남도", "경남"),
            Map.entry("제주특별자치도", "제주"));

    private SidoName() {
    }

    /** 정식 시도명 → 축약 시도명. 이미 축약형이거나 미지의 값은 그대로 돌려준다. */
    public static String toShort(String sido) {
        if (sido == null) {
            return null;
        }
        return SHORT_NAMES.getOrDefault(sido, sido);
    }
}
