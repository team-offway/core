package com.offway.core.common.external.controller;

import com.offway.core.common.external.controller.dto.ExternalApiStatusResponse;
import com.offway.core.common.response.ApiResponseBody;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "어드민 - 외부 API")
public interface AdminExternalApiApi {

    @Operation(
            summary = "외부 API 연동 현황",
            description = """
                    우리가 붙어 있는 외부 API 전부의 사용량·주체·데이터 출처를 한 번에 돌려준다.

                    `/api/v1/quotas` 와 다른 점은 **기간**이다. 그쪽은 오늘치뿐이라 어제를 못 보고,
                    월배치가 튀는 날을 가려낼 수 없다.
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "401", description = "로그인하지 않았거나 토큰이 만료됨")
    @ApiResponse(responseCode = "403", description = "어드민 권한이 없음")
    ApiResponseBody<ExternalApiStatusResponse> status(
            @Parameter(description = "오늘부터 거슬러 셀 일수. 기본 14, 상한 90. 벗어나면 거절하지 않고 자른다")
            Integer days);
}
