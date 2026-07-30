package com.offway.core.trip.infrastructure.datalab;

import com.offway.core.trip.infrastructure.datalab.dto.TourVisitorResult;
import java.time.Duration;
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
     * @param maxWait 이 <b>한 건</b>을 기다릴 상한 — 호출자에게 남은 시간 예산이다. 구현은 자체 timeout 과 이 값 중
     *     <b>짧은 쪽</b>만 기다린다. 페이지·월을 순차로 도는 집계에서, 예산이 거의 다 떨어진 시점에 시작한 마지막
     *     요청이 자체 timeout 만큼 더 대기해 전체 상한을 넘기는 것을 막는다.
     */
    TourVisitorResult findRegionVisitors(
            LocalDate from, LocalDate to, int pageNo, int numOfRows, Duration maxWait);
}
