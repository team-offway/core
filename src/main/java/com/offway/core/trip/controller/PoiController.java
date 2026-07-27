package com.offway.core.trip.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.trip.controller.dto.PoiDetailResponse;
import com.offway.core.trip.service.PoiDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pois")
@RequiredArgsConstructor
public class PoiController implements PoiApi {

    private final PoiDetailService poiDetailService;

    @Override
    @GetMapping("/{contentId}")
    public ApiResponseBody<PoiDetailResponse> detail(@PathVariable String contentId) {
        return ApiResponseBody.ok(PoiDetailResponse.from(poiDetailService.detail(contentId)));
    }
}
