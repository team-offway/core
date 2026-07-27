package com.offway.core.weather.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.weather.controller.dto.AirResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 대기질 API 문서 계약. 매핑은 구현체({@link AirController})가 소유한다. */
@Tag(name = "대기질", description = "지역 시도 단위 실시간 미세먼지")
public interface AirApi {

    @Operation(summary = "지역 대기질", description = "지역 시도의 실시간 미세먼지·초미세먼지·통합등급. 데이터가 없으면 data=null.")
    @ApiResponse(responseCode = "200", description = "조회 성공 (없으면 data=null)")
    @ApiResponse(responseCode = "502", description = "대기오염정보(에어코리아) 조회 실패")
    ApiResponseBody<AirResponse> air(
            @Parameter(description = "지역 시도명(정식/축약)", example = "강원특별자치도") String region);
}
