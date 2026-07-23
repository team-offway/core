package com.offway.core.trip.infrastructure.datalab;

import com.offway.core.trip.infrastructure.datalab.dto.TourVisitorResult;
import java.time.LocalDate;

/**
 * 관광빅데이터(한국관광공사 DataLabService) 조회 port — 기초 시군구별 일별 방문자수.
 *
 * <p>랭킹·한산도 뱃지(평일 붐빔 정도)의 재료. trip 도메인·서비스는 이 인터페이스에만 의존한다. 키가 없으면 외부 호출 없이 빈 결과를
 * 돌려준다(로컬 실행성 불변식).
 */
public interface TourDataLabClient {

    /**
     * 기간 내 기초 시군구별 일별 방문자수(locgoRegnVisitrDDList).
     *
     * @param from 시작일 (포함)
     * @param to 종료일 (포함)
     * @param pageNo 페이지 번호 (1부터)
     * @param numOfRows 페이지 크기
     */
    TourVisitorResult findRegionVisitors(LocalDate from, LocalDate to, int pageNo, int numOfRows);
}
