package com.offway.core.weather.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.weather.controller.dto.AirResponse;
import com.offway.core.weather.service.AirQualityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/air")
@RequiredArgsConstructor
public class AirController implements AirApi {

    private final AirQualityService airQualityService;

    @Override
    @GetMapping
    public ApiResponseBody<AirResponse> air(@RequestParam String region) {
        return ApiResponseBody.ok(airQualityService.byRegionSido(region).map(AirResponse::from).orElse(null));
    }
}
