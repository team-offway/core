package com.offway.core.itinerary.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.itinerary.controller.dto.CourseGenerateRequest;
import com.offway.core.itinerary.controller.dto.CourseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 코스 생성 API 문서 계약. 매핑·검증은 구현체({@link CourseGenerateController})가 소유한다. */
@Tag(name = "코스", description = "지역+조건으로 날짜별 타임라인 코스 자동 생성")
public interface CourseGenerateApi {

    @Operation(
            summary = "코스 자동 생성",
            description = "지역·일수·밀도·이동수단으로 날짜별 타임라인(관광·식사·숙박 슬롯 + 이동시간 + 지도 좌표)과 적용 혜택을 만든다."
                    + " 동선은 이동수단 기반 최근접 정렬(자차 기준 interim)로 배치한다.")
    @ApiResponse(responseCode = "200", description = "생성 성공")
    @ApiResponse(responseCode = "400", description = "지역 누락 · 일수 범위(1~3) 초과 · 좌표 범위 초과 · 필수값 누락")
    @ApiResponse(responseCode = "404", description = "해당 지역으로 만들 수 있는 코스가 없음(볼거리 부족)")
    @ApiResponse(responseCode = "502", description = "관광정보(TourAPI) 조회 실패")
    ApiResponseBody<CourseResponse> generate(CourseGenerateRequest request);
}
