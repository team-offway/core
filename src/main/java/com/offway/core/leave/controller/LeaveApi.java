package com.offway.core.leave.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.leave.controller.dto.AvailableTimeRequest;
import com.offway.core.leave.controller.dto.AvailableTimeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 연차·가용시간 API 문서 계약. 매핑·검증 어노테이션은 구현체({@link LeaveController})가 소유한다. */
@Tag(name = "연차", description = "연차 기반 가용시간(LNT)·샌드위치 연휴")
public interface LeaveApi {

    @Operation(summary = "가용 시간(LNT) 산출", description = "확정된 날짜 구간으로 여행일수·소모 연차·이동 한계를 산출한다.")
    @ApiResponse(responseCode = "200", description = "산출 성공")
    @ApiResponse(responseCode = "400", description = "날짜 형식 오류 · 종료일이 시작일보다 앞섬 · 여행 구간이 2박 3일 초과")
    @ApiResponse(responseCode = "502", description = "공휴일 정보(특일정보) 조회 실패")
    ApiResponseBody<AvailableTimeResponse> availableTime(AvailableTimeRequest request);
}
