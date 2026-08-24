package com.offway.core.common.external.controller;

import com.offway.core.common.external.controller.dto.QuotaResponse;
import com.offway.core.common.response.ApiResponseBody;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "운영")
public interface QuotaApi {

    @Operation(
            summary = "외부 API 오늘자 한도 현황",
            description = """
                    오늘(KST) 외부 API 를 얼마나 썼는지 본다.

                    **한도는 계정이 아니라 활용신청 단위다.** 키는 하나지만 서비스마다 따로 한도가 있어,
                    관광정보가 말라도 특일정보는 멀쩡하다. 반대로 같은 서비스 안의 오퍼레이션들은 한 한도를 나눠 쓴다.

                    가장 빡빡한 것부터 본다 — TMAP 경유지최적화 50 · 에어코리아 500 · 관광정보 1,000.

                    한 번도 안 부른 API 도 0 으로 함께 나간다. 빠져 있으면 "안 쓴 것" 과 "안 센 것" 이 구분되지 않는다.""")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    ApiResponseBody<QuotaResponse> quotas();
}
