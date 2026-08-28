package com.offway.core.transport.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * TAGO 도시코드의 시도 구분. 도시코드 앞 두 자리가 시도를 뜻한다({@code 32010} 춘천 → {@code 32} 강원).
 *
 * <p>이 구분이 있어야 <b>동명 시군구를 갈라낼 수 있다.</b> 고성군은 강원(32)과 경남(38) 양쪽에 있고 TAGO 는 경남만 담는다
 * — 지명만 맞추면 강원 고성군까지 커버된다고 잘못 답한다. 같은 함정이 동구·중구·서구·남구·북구에도 있다.
 *
 * <p>서울은 TAGO 대상이 아니라(별도 TOPIS) 여기 선언돼 있어도 도시 목록에 나타나지 않는다. 즉 자연히 미커버로 판정된다.
 */
public enum BusSido {
    SEOUL("서울특별시", 11),
    SEJONG("세종특별자치시", 12),
    BUSAN("부산광역시", 21),
    DAEGU("대구광역시", 22),
    INCHEON("인천광역시", 23),
    GWANGJU("광주광역시", 24),
    DAEJEON("대전광역시", 25),
    ULSAN("울산광역시", 26),
    GYEONGGI("경기도", 31),
    GANGWON("강원특별자치도", 32),
    CHUNGBUK("충청북도", 33),
    CHUNGNAM("충청남도", 34),
    JEONBUK("전북특별자치도", 35),
    JEONNAM("전남광주통합특별시", 36),
    GYEONGBUK("경상북도", 37),
    GYEONGNAM("경상남도", 38),
    JEJU("제주특별자치도", 39);

    /** 시군구 도시코드는 5자리({@code 32010}), 시 전체를 한 코드로 두는 광역시는 2자리({@code 21})다. */
    private static final int SIGUNGU_CODE_DIVISOR = 1_000;

    private final String sidoName;
    private final int prefix;

    BusSido(String sidoName, int prefix) {
        this.sidoName = sidoName;
        this.prefix = prefix;
    }

    /** 시도명으로 찾는다. 알 수 없는 이름이면 빈 Optional. */
    public static Optional<BusSido> of(String sidoName) {
        return Arrays.stream(values())
                .filter(sido -> sido.sidoName.equals(sidoName))
                .findFirst();
    }

    /** 이 도시코드가 이 시도에 속하는가. */
    public boolean owns(int cityCode) {
        int codePrefix = cityCode < SIGUNGU_CODE_DIVISOR ? cityCode : cityCode / SIGUNGU_CODE_DIVISOR;
        return codePrefix == prefix;
    }
}
