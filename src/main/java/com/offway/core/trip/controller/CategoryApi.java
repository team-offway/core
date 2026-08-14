package com.offway.core.trip.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.trip.controller.dto.CategoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 필터칩 카테고리 API 문서 계약. 매핑은 구현체({@link CategoryController})가 소유한다. */
@Tag(name = "카테고리", description = "여행지 필터칩(무드/유형)")
public interface CategoryApi {

    @Operation(
            summary = "필터칩 카테고리 목록",
            description = """
                    결과 필터/재정렬에 쓰는 카테고리 칩을 노출 순서대로 반환한다.

                    칩마다 **그 칩으로 좁혔을 때 나오는 인구감소지역 수**(`regionCount`)를 함께 준다 —
                    `GET /api/v1/regions?category={key}` 의 `pageResponse.totalElements` 와 같은 값이다.
                    `ALL` 은 전체 지역 수다. 개수는 적재된 지역 콘텐츠에서 세며 **외부 API 를 부르지 않는다**.
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공 (콘텐츠 적재 전이면 ALL 을 제외한 개수가 0)")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    ApiResponseBody<CategoryResponse> categories();
}
