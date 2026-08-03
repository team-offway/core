package com.offway.core.weather.infrastructure.kma;

import com.offway.core.weather.domain.SigunguKey;
import com.offway.core.weather.domain.TourClimateIndex;
import java.time.LocalDate;
import java.util.Map;

/**
 * 기상청 관광기후지수 port(#130) — "그날 그 시군구가 관광하기 좋은가" 를 등급으로 준다.
 *
 * <p><b>날짜별로도, 지역별로도 나눠 부르지 않는다.</b> 이 API 는 요청 하나에 <b>전 기간 × 전국</b>을 통째로 준다(실측
 * 9일 × 236곳 = 2,124건, 347KB, 2.5초). 지역별로 부르면 같은 응답을 89번, 날짜별로 부르면 9번 받는 셈이다.
 */
public interface TourClimateIndexClient {

    /**
     * 조회 가능한 전 기간의 시군구별 관광기후지수 — 날짜별로 묶어서 준다.
     *
     * <p>키 없음·조회 실패·빈 응답이면 빈 Map(조용히 실패하지 않게 어댑터가 로그를 남긴다).
     */
    Map<LocalDate, Map<SigunguKey, TourClimateIndex>> forecast();
}
