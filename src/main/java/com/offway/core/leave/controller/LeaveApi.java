package com.offway.core.leave.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.leave.controller.dto.AvailableTimeRequest;
import com.offway.core.leave.controller.dto.AvailableTimeResponse;
import com.offway.core.leave.controller.dto.SandwichResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;

/** 연차·가용시간 API 문서 계약. 매핑·검증 어노테이션은 구현체({@link LeaveController})가 소유한다. */
@Tag(name = "연차", description = "연차 기반 가용시간(LNT)·샌드위치 연휴")
public interface LeaveApi {

    @Operation(summary = "가용 시간(LNT) 산출", description = "확정된 날짜 구간으로 여행일수·소모 연차·이동 한계를 산출한다.")
    @ApiResponse(responseCode = "200", description = "산출 성공")
    @ApiResponse(responseCode = "400", description = "날짜 형식 오류 · 종료일이 시작일보다 앞섬 · 여행 구간이 2박 3일 초과")
    @ApiResponse(responseCode = "502", description = "공휴일 정보(특일정보) 조회 실패")
    ApiResponseBody<AvailableTimeResponse> availableTime(AvailableTimeRequest request);

    @Operation(summary = "샌드위치 연휴 추천", description = "조회 기간 안에서 최소 연차로 최대 휴식이 되는 황금 연차를 효율 순으로 추천한다.")
    @ApiResponse(responseCode = "200", description = "추천 성공 (없으면 빈 목록)")
    @ApiResponse(responseCode = "400", description = "fromDate 누락·형식 오류 · 조회 개월 수가 1~12 범위 밖")
    @ApiResponse(responseCode = "502", description = "공휴일 정보(특일정보) 조회 실패")
    ApiResponseBody<SandwichResponse> sandwich(
            @Parameter(description = "조회 시작일", example = "2026-05-01") LocalDate fromDate,
            @Parameter(description = "조회 개월 수 (1~12, 기본 6)", example = "6") int months,
            @Parameter(description = "남은 연차 (있으면 그 이하로 가능한 연휴만)", example = "8") Double remainingLeave);
}
