package com.offway.core.trip.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.trip.controller.dto.HomeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 홈 API 문서 계약. 매핑은 구현체({@link HomeController})가 소유한다. */
@Tag(name = "홈", description = "남은 연차 · 필터칩 · 이번주 추천 지역")
public interface HomeApi {

    @Operation(
            summary = "홈",
            description = "남은 연차 + 필터칩 + 이번주 추천 지역(랭킹 top-N, 대표 이미지·categories·한산도·대표 혜택 뱃지).")
    @ApiResponse(responseCode = "200", description = "조회 성공 (외부 관광정보 실패 시 이미지 없이 degrade — 502 아님)")
    ApiResponseBody<HomeResponse> home(
            @Parameter(description = "소유 키 헤더 (없으면 남은 연차는 null)", example = "guest-abc123")
                    String guestId);
}
