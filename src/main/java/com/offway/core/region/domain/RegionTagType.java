package com.offway.core.region.domain;

/**
 * 지역에 붙는 라벨 종류. 정책 매칭·필터의 기준.
 *
 * <p>번호가 아니라 이름으로 식별하며 append-only 로 관리한다. 새 정책 대상 태그(디지털관광주민증·반값여행 등)는 그 정책의 실제 대상 지역을
 * 확보할 때 여기에 추가하고 region_tag 에 행을 시딩한다.
 */
public enum RegionTagType {

    /** 인구감소지역 (행안부 고시 89). 현재 전 지역이 해당하나, 향후 다른 유형 지역 추가 시 구분자가 된다. */
    POPULATION_DECLINE
}
