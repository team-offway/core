package com.offway.core.weather.domain;

import java.util.Map;

/**
 * 시도 명칭 매핑 — region 의 정식 시도명("강원특별자치도")을 에어코리아 sidoName 파라미터 값("강원")으로 바꾼다. 에어코리아는
 * 축약 시도명을 쓴다.
 */
public final class SidoName {

    private static final Map<String, String> AIR_KOREA = Map.ofEntries(
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
            Map.entry("경상북도", "경북"),
            Map.entry("경상남도", "경남"),
            Map.entry("제주특별자치도", "제주"));

    private SidoName() {
    }

    /** 정식 시도명 → 에어코리아 축약 시도명. 이미 축약형이거나 미지의 값은 그대로 돌려준다. */
    public static String toAirKorea(String sido) {
        if (sido == null) {
            return null;
        }
        return AIR_KOREA.getOrDefault(sido, sido);
    }
}
