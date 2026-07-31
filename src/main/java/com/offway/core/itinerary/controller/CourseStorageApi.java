package com.offway.core.itinerary.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.leave.controller.dto.MyLeaveResponse;
import com.offway.core.itinerary.controller.dto.CourseLeaveDeductionRequest;
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

    @Operation(
            summary = "내 코스 삭제",
            description = """
                    소유한 코스를 지운다. 하위 일정·슬롯도 함께 지워진다(hard delete).

                    없는 코스와 남의 코스를 모두 404 로 답한다 — 403 으로 나누면 "그 ID 는 존재한다" 를
                    알려주는 셈이라 ID 를 훑어 남의 코스 존재를 확인할 수 있다.""")
    @ApiResponse(responseCode = "200", description = "삭제 성공 (data 는 null — 204 를 쓰지 않는다)")
    @ApiResponse(responseCode = "400", description = "X-Guest-Id 헤더 누락")
    @ApiResponse(responseCode = "404", description = "코스가 없거나 소유자가 아님")
    ApiResponseBody<Void> deleteCourse(
            @Parameter(description = "게스트 식별자", example = "guest-abc123") String guestId,
            @Parameter(description = "코스 ID", example = "12") long courseId);

    @Operation(
            summary = "코스 확정 — 연차 차감",
            description = """
                    저장한 코스의 여행 날짜만큼 연차를 차감한다. 와이어프레임의 "연차를 차감할까요?" 확인에 대응하는
                    명시적 액션이라, 코스 저장만으로는 차감되지 않는다.

                    차감 일수는 **서버가 다시 계산한다** — 저장된 여행 날짜 구간의 평일에서 공휴일을 뺀 값이고,
                    반차로 시작하면 0.5 를 뺀다. 클라이언트가 일수나 날짜를 보내지 않는 이유는, 보낸 만큼
                    차감량이 바뀌면 임의 차감이 되기 때문이다.

                    **멱등하다** — 같은 코스로 다시 호출해도 내역이 늘지 않고 현재 상태를 그대로 준다.

                    **잔여가 부족해도 막지 않는다** — 남은 연차는 음수가 될 수 있다. 경고와 확인은 프론트가 맡는다.""")
    @ApiResponse(responseCode = "200", description = "차감 성공 (또는 이미 차감된 코스 — 상태를 그대로 준다)")
    @ApiResponse(responseCode = "400", description = "X-Guest-Id 헤더 누락·형식 오류, 또는 저장 시 여행 날짜를 넣지 않은 코스")
    @ApiResponse(responseCode = "404", description = "코스가 없거나 소유자가 아님")
    @ApiResponse(responseCode = "502", description = "공휴일 조회(특일정보) 실패로 차감 일수를 계산할 수 없음")
    ApiResponseBody<MyLeaveResponse> deductLeave(
            String guestId, long courseId, CourseLeaveDeductionRequest request);
}
