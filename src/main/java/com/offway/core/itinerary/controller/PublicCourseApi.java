package com.offway.core.itinerary.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.itinerary.controller.dto.CourseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "코스 공유")
public interface PublicCourseApi {

    @Operation(
            summary = "공유 링크로 코스 보기",
            description =
                    """
                    공유 토큰으로 코스 하나를 읽는다. **인증이 필요 없다** — 링크를 받은 사람에게는 계정이 없다.

                    소유자 식별자와 내부 `courseId` 는 실리지 않는다. 보기 전용이라 수정·삭제·연차 차감은 이 경로로 할 수 없다.
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "없는 공유 링크 (토큰이 존재하지 않음)")
    @ApiResponse(responseCode = "410", description = "게시자가 코스를 삭제해 더는 볼 수 없음")
    ApiResponseBody<CourseResponse> shared(
            @Parameter(description = "공유 토큰", example = "a1B2c3D4e5F6g7H8i9J0kL") String shareToken);
}
