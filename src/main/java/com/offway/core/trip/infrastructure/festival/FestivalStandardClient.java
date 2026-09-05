package com.offway.core.trip.infrastructure.festival;

import com.offway.core.trip.infrastructure.festival.dto.StandardFestivalResult;
import java.time.Duration;

/**
 * 전국문화축제표준데이터 조회 port(#433).
 *
 * <p>지자체 229곳이 같은 스키마로 올린 것을 공공데이터포털이 <b>매월 초 병합</b>해 전국 단위로 낸다.
 * 그래서 지역별 어댑터 89개를 만들 필요가 없다.
 *
 * <p>한도는 TourAPI 와 <b>별개</b>다(개발계정 10,000/일). 관광 한도가 마른 날에도 이 적재는 돈다.
 *
 * <p>키가 없으면 외부 호출 없이 빈 결과를 돌려준다(로컬 실행성 불변식).
 */
public interface FestivalStandardClient {

    /**
     * 전국 축제 목록 한 페이지.
     *
     * @param pageNo 페이지 번호 (1부터)
     * @param numOfRows 페이지 크기
     * @param maxWait 이 <b>한 건</b>을 기다릴 상한 — 호출자에게 남은 시간 예산이다. 구현은 자체
     *     timeout 과 이 값 중 <b>짧은 쪽</b>만 기다린다
     */
    StandardFestivalResult findAll(int pageNo, int numOfRows, Duration maxWait);
}
