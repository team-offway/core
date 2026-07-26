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
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "502", description = "외부 관광정보(방문자 통계 · TourAPI 콘텐츠) 조회 실패")
    ApiResponseBody<HomeResponse> home(
            @Parameter(description = "남은 연차 (게스트 — 클라이언트 보유값)", example = "13") Integer remainingLeave);
}
