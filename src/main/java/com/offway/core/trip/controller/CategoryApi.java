package com.offway.core.trip.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.trip.controller.dto.CategoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 필터칩 카테고리 API 문서 계약. 매핑은 구현체({@link CategoryController})가 소유한다. */
@Tag(name = "카테고리", description = "여행지 필터칩(무드/유형)")
public interface CategoryApi {

    @Operation(summary = "필터칩 카테고리 목록", description = "결과 필터/재정렬에 쓰는 카테고리 칩을 노출 순서대로 반환한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    ApiResponseBody<CategoryResponse> categories();
}
