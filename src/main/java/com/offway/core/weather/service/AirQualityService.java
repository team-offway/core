package com.offway.core.weather.service;

import com.offway.core.weather.domain.SidoName;
import com.offway.core.weather.infrastructure.airkorea.AirKoreaClient;
import com.offway.core.weather.infrastructure.airkorea.dto.AirQuality;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 대기질 조회 — weather 가 다른 도메인(trip 지역·itinerary 코스)에 노출하는 공개 서비스. 지역 시도명으로 실시간 대기질 요약을 준다.
 * 외부(에어코리아) 호출은 부가 정보라 실패 시 빈 값.
 */
@Service
@RequiredArgsConstructor
public class AirQualityService {

    private final AirKoreaClient airKoreaClient;

    /** 지역 시도명(정식/축약 모두)에 대한 실시간 대기질. 없으면 빈 Optional. */
    public Optional<AirQuality> byRegionSido(String sido) {
        return airKoreaClient.realtimeBySido(SidoName.toAirKorea(sido));
    }
}
