package com.offway.core.trip.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.trip.controller.dto.RegionDetailResponse;
import com.offway.core.trip.service.RegionDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionDetailController implements RegionDetailApi {

    private final RegionDetailService regionDetailService;

    @Override
    @GetMapping("/{regionId}")
    public ApiResponseBody<RegionDetailResponse> detail(@PathVariable Long regionId) {
        return ApiResponseBody.ok(RegionDetailResponse.from(regionDetailService.detail(regionId)));
    }
}
