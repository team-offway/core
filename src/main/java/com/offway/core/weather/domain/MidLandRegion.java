package com.offway.core.weather.domain;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * 기상청 중기 육상예보 구역(#129) — 전국이 10개 광역 구역으로 나뉜다. 단기예보가 좌표 격자를 쓰는 것과 달리 중기예보는 이 구역
 * 코드({@code regId})로 조회한다.
 *
 * <p><b>강원만 시군으로 한 번 더 갈린다.</b> 나머지는 시도명만으로 결정되지만 강원은 백두대간을 경계로 영동·영서 예보가 따로
 * 나온다. 태백·삼척·고성·양양처럼 동해에 붙은 곳에 영서 예보를 주면 눈·바람이 통째로 어긋난다.
 *
 * <p>구역 코드는 실호출로 10개 전부 확인했다(2026-08-03).
 */
public enum MidLandRegion {

    SEOUL_INCHEON_GYEONGGI("11B00000", Set.of("서울특별시", "인천광역시", "경기도")),
    GANGWON_YEONGSEO("11D10000", Set.of("강원도", "강원특별자치도")),
    /** 영동은 시군으로 가려낸다 — 아래 {@link #YEONGDONG_SIGUNGU} 에 해당하면 영서 대신 이쪽이다. */
    GANGWON_YEONGDONG("11D20000", Set.of()),
    CHUNGBUK("11C10000", Set.of("충청북도")),
    DAEJEON_SEJONG_CHUNGNAM("11C20000", Set.of("대전광역시", "세종특별자치시", "충청남도")),
    JEONBUK("11F10000", Set.of("전라북도", "전북특별자치도")),
    GWANGJU_JEONNAM("11F20000", Set.of("광주광역시", "전라남도")),
    DAEGU_GYEONGBUK("11H10000", Set.of("대구광역시", "경상북도")),
    BUSAN_ULSAN_GYEONGNAM("11H20000", Set.of("부산광역시", "울산광역시", "경상남도")),
    JEJU("11G00000", Set.of("제주특별자치도"));

    /**
     * 강원 영동에 속하는 시군. 기상청 예보구역 기준이다.
     *
     * <p>태백은 내륙 고지대라 영서로 착각하기 쉬우나 기상청은 영동으로 묶는다.
     */
    private static final Set<String> YEONGDONG_SIGUNGU =
            Set.of("강릉시", "동해시", "속초시", "삼척시", "태백시", "고성군", "양양군");

    private final String regId;
    private final Set<String> sidoNames;

    MidLandRegion(String regId, Set<String> sidoNames) {
        this.regId = regId;
        this.sidoNames = sidoNames;
    }

    /** 중기예보 조회 파라미터로 쓰는 구역 코드. */
    public String regId() {
        return regId;
    }

    /**
     * 시도·시군구로 예보 구역을 찾는다. 못 찾으면 빈 Optional — 호출자가 날씨를 생략한다.
     *
     * <p>강원을 먼저 가려내는 이유: 영동 시군은 시도명으로는 영서와 구분되지 않는다.
     */
    public static Optional<MidLandRegion> of(String sido, String sigungu) {
        if (sido == null) {
            return Optional.empty();
        }
        if (isGangwon(sido) && sigungu != null && YEONGDONG_SIGUNGU.contains(sigungu)) {
            return Optional.of(GANGWON_YEONGDONG);
        }
        return Arrays.stream(values()).filter(region -> region.sidoNames.contains(sido)).findFirst();
    }

    private static boolean isGangwon(String sido) {
        return GANGWON_YEONGSEO.sidoNames.contains(sido);
    }
}
