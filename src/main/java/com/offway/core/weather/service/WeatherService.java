package com.offway.core.weather.service;

import com.offway.core.weather.infrastructure.kma.KmaWeatherClient;
import com.offway.core.weather.domain.DailyWeather;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 날씨 조회 — weather 가 다른 도메인(trip 지역·itinerary 코스)에 노출하는 공개 서비스. 좌표·날짜의 날씨 요약을 준다. 외부(기상청)
 * 호출은 read-timeout 이 있어 트랜잭션 밖에서 쓴다(부가 정보라 실패 시 빈 값).
 */
@Service
@RequiredArgsConstructor
public class WeatherService {

    private final KmaWeatherClient kmaWeatherClient;

    /** 좌표 지점의 해당 날짜 날씨(없으면 빈 Optional). */
    public Optional<DailyWeather> dailyWeather(double lat, double lng, LocalDate date) {
        return kmaWeatherClient.dailyForecast(lat, lng, date);
    }
}
