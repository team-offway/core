package com.offway.core.trip.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.trip.controller.dto.RegionRecommendRequest;
import com.offway.core.trip.controller.dto.RegionRecommendResponse;
import com.offway.core.transport.service.OriginSuggestService;
import com.offway.core.transport.service.dto.ResolvedOrigin;
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
    private final OriginSuggestService originSuggestService;

    @Override
    @PostMapping("/recommendations")
    public ApiResponseBody<RegionRecommendResponse> recommend(@Valid @RequestBody RegionRecommendRequest request) {
        // 출발지 해석은 코스 생성과 같은 규칙이다 — OriginSuggestService 가 우선순위를 소유한다.
        ResolvedOrigin origin = originSuggestService.resolveOrDefault(
                request.originCode(), request.originName(), request.originLat(), request.originLng());
        return ApiResponseBody.ok(
                RegionRecommendResponse.from(regionRecommendationService.recommend(request.toCommand(origin))));
    }
}
