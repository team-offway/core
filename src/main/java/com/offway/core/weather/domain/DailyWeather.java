package com.offway.core.weather.domain;

import java.time.LocalDate;

/**
 * 하루 날씨 요약 — 기상청 단기예보(동네예보)를 하루 단위로 집계한 결과. 코스·지역에 날씨 뱃지로 붙인다.
 *
 * @param date 예보 날짜
 * @param minTemp 최저기온(℃, 없으면 null)
 * @param maxTemp 최고기온(℃, 없으면 null)
 * @param sky 하늘 상태
 * @param rainProbability 강수확률 최대(%, 없으면 null)
 */
public record DailyWeather(
        LocalDate date, Integer minTemp, Integer maxTemp, SkyState sky, Integer rainProbability) {
}
