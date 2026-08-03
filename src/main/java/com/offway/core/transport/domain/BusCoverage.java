package com.offway.core.transport.domain;

import java.util.List;
import java.util.Objects;

/**
 * TAGO 시내버스가 담는 도시 목록 — 어떤 시군구가 커버되는지 판정한다.
 *
 * <p>TAGO 시내버스는 전국이 아니라 <b>138개 지자체</b>만 담는다(서울조차 별도 TOPIS). 우리 89개 인구감소지역 중 13곳이
 * 빠져 있는데, 미커버 지역은 오류가 아니라 <b>정상 응답 + 빈 결과</b>로 와서 "주변에 정류소 없음"과 구분되지 않는다. 그
 * 구분을 이 목록이 만든다 — "정선에 버스가 없다"가 아니라 "정선은 TAGO 에 데이터가 없다"로 답하기 위해서다.
 */
public record BusCoverage(List<BusCity> cities) {

    public BusCoverage {
        Objects.requireNonNull(cities, "도시 목록은 필수입니다");
        cities = List.copyOf(cities);
    }

    /** 이 시군구가 TAGO 시내버스 대상인가. 시도로 걸러 동명 시군구를 갈라낸다. */
    public boolean covers(String sido, String sigungu) {
        return BusSido.of(sido)
                .map(busSido -> cities.stream()
                        .filter(city -> busSido.owns(city.code()))
                        .anyMatch(city -> city.covers(sigungu)))
                .orElse(false);
    }
}
