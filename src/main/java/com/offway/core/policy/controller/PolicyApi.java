package com.offway.core.policy.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.policy.controller.dto.PolicyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 정책(7대 혜택) API 문서 계약. 매핑·검증은 구현체({@link PolicyController})가 소유한다. */
@Tag(name = "정책", description = "7대 여행 지원 혜택 조회")
public interface PolicyApi {

    @Operation(summary = "정책 상세", description = "정책 정보와 이 혜택이 되는 여행지 목록(정책→지역 역방향)을 반환한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "정책이 없거나 노출 대상이 아님(미검증 포함)")
    ApiResponseBody<PolicyResponse> getPolicy(@Parameter(description = "정책 ID", example = "1") Long policyId);
}
