package com.offway.core.trip.infrastructure.crowd;

import com.offway.core.trip.infrastructure.crowd.dto.AttractionCrowd;
import java.time.Duration;
import java.util.List;

/**
 * 관광지 집중률 예측 port(#565) — 도메인이 의존하는 쪽.
 *
 * <p>{@code TatsCnctrRateService/tatsCnctrRatedList} 를 감싼다.
 */
public interface AttractionCrowdClient {

    /**
     * 한 지역의 관광지 집중률 예측 전량 — 관광지 × 30일.
     *
     * @param legalCode 법정 시군구코드 5자리. 앞 2자리가 시도({@code areaCd})다
     * @param maxWait 이 호출에 허용한 시간. 클라이언트 자신의 상한과 짧은 쪽을 따른다
     * @return 그 지역의 모든 (관광지, 날짜) 쌍. 집중률이 없는 지역이면 빈 목록
     */
    List<AttractionCrowd> findByRegion(String legalCode, Duration maxWait);
}
