package com.offway.core.trip.controller.dto;

import com.offway.core.transport.domain.TransportMode;
import com.offway.core.trip.service.dto.RecommendRegions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 여행지 추천 요청 — API 계약.
 *
 * @param originLat 출발지 위도
 * @param originLng 출발지 경도
 * @param transport 이동수단 (CAR·TRANSIT)
 * @param maxReachMinutes 편도 도달 한계(분) — 가용시간(LNT) 산출 응답에서 받아 넘긴다
 */
public record RegionRecommendRequest(
        @Schema(example = "37.49", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull @DecimalMin("-90") @DecimalMax("90") Double originLat,
        @Schema(example = "127.02", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull @DecimalMin("-180") @DecimalMax("180") Double originLng,
        @Schema(example = "CAR", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull TransportMode transport,
        @Schema(description = "편도 도달 한계(분)", example = "420", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull @Positive Integer maxReachMinutes) {

    public RecommendRegions toCommand() {
        return new RecommendRegions(originLat, originLng, transport, maxReachMinutes);
    }
}
