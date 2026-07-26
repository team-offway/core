package com.offway.core.trip.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.trip.controller.dto.RegionRecommendRequest;
import com.offway.core.trip.controller.dto.RegionRecommendResponse;
import com.offway.core.trip.service.RegionRecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionRecommendController implements RegionRecommendApi {

    private final RegionRecommendationService regionRecommendationService;

    @Override
    @PostMapping("/recommendations")
    public ApiResponseBody<RegionRecommendResponse> recommend(@Valid @RequestBody RegionRecommendRequest request) {
        return ApiResponseBody.ok(RegionRecommendResponse.from(regionRecommendationService.recommend(request.toCommand())));
    }
}
