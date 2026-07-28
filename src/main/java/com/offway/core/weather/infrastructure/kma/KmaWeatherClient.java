package com.offway.core.weather.infrastructure.kma;

import com.offway.core.weather.domain.DailyWeather;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 기상청 단기예보(동네예보) 조회 port. 위경도·날짜로 그 날의 날씨 요약을 준다.
 *
 * <p>키가 없거나 호출/파싱 실패, 또는 예보 범위(발표일 기준 ~3일) 밖이면 빈 Optional — 날씨는 부가 정보라 실패로 코스를 막지 않는다.
 */
public interface KmaWeatherClient {

    /** 좌표 지점의 해당 날짜 날씨 요약. 없으면 빈 Optional. */
    Optional<DailyWeather> dailyForecast(double lat, double lng, LocalDate date);
}
