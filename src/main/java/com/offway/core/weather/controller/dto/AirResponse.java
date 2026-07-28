package com.offway.core.weather.controller.dto;

import com.offway.core.weather.domain.AirQuality;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 대기질 응답 — API 계약. 시도 단위 미세먼지·초미세먼지·통합등급.
 *
 * @param pm10 미세먼지(㎍/㎥, 없으면 null)
 * @param pm25 초미세먼지(㎍/㎥, 없으면 null)
 * @param grade 통합등급 코드(GOOD·MODERATE·BAD·VERY_BAD·UNKNOWN)
 * @param gradeLabel 통합등급 한글(좋음·보통·나쁨·매우나쁨)
 */
public record AirResponse(
        @Schema(example = "29") Integer pm10,
        @Schema(example = "15") Integer pm25,
        @Schema(example = "GOOD") String grade,
        @Schema(example = "좋음") String gradeLabel) {

    public static AirResponse from(AirQuality air) {
        return new AirResponse(air.pm10(), air.pm25(), air.grade().name(), air.grade().label());
    }
}
