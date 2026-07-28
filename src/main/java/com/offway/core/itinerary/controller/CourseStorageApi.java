package com.offway.core.itinerary.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.itinerary.controller.dto.CourseResponse;
import com.offway.core.itinerary.controller.dto.CourseSaveRequest;
import com.offway.core.itinerary.controller.dto.CourseSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

/** 코스 저장·조회 API 문서 계약. 매핑은 구현체({@link CourseStorageController})가 소유한다. */
@Tag(name = "내 코스", description = "코스 저장 · 내 코스 목록·상세")
public interface CourseStorageApi {

    @Operation(summary = "코스 저장", description = "생성한 코스를 게스트의 '내 코스'로 저장한다.")
    @ApiResponse(responseCode = "201", description = "저장 성공")
    @ApiResponse(responseCode = "400", description = "게스트 ID 누락 · 코스 구성 오류(순서·좌표 등)")
    ApiResponseBody<CourseResponse> save(
            @Parameter(description = "게스트 식별자", example = "guest-abc123") String guestId, CourseSaveRequest request);

    @Operation(summary = "내 코스 목록", description = "게스트가 저장한 코스 요약을 최신순으로.")
    @ApiResponse(responseCode = "200", description = "조회 성공(없으면 빈 목록)")
    @ApiResponse(responseCode = "400", description = "게스트 ID 누락")
    ApiResponseBody<List<CourseSummaryResponse>> myCourses(
            @Parameter(description = "게스트 식별자", example = "guest-abc123") String guestId);

    @Operation(summary = "코스 상세", description = "저장 코스의 날짜별 타임라인과 혜택.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = "게스트 ID 누락")
    @ApiResponse(responseCode = "404", description = "요청한 코스가 없거나 소유자가 아님")
    ApiResponseBody<CourseResponse> course(
            @Parameter(description = "게스트 식별자", example = "guest-abc123") String guestId,
            @Parameter(description = "코스 ID", example = "1") long courseId);
}
