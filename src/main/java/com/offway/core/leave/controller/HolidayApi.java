package com.offway.core.leave.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.leave.controller.dto.HolidayResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 공휴일 조회 API 문서 계약. 매핑은 구현체({@link HolidayController})가 소유한다. */
@Tag(name = "공휴일", description = "연 단위 공휴일 목록")
public interface HolidayApi {

    @Operation(
            summary = "한 해의 공휴일 목록",
            description = """
                    그 해의 공휴일을 날짜 오름차순으로 준다.

                    **앱의 로컬 계산을 서버와 같은 답으로 맞추기 위한 것이다.** 가용시간(`POST /leaves/available-time`)
                    호출이 실패해 앱이 자체 계산으로 폴백할 때, 공휴일을 모르면 주말만 걸러 공휴일이 낀 주의
                    차감일을 실제보다 많게 낸다. 이 목록을 한 번 받아 두면 폴백도 같은 답을 낸다.

                    **조회할 수 있는 해는 지난해부터 내년까지다.** 그 밖은 400 으로 거절한다 — 서버가 미리 채워
                    두는 범위 밖이라, 한 요청이 외부(특일정보) 호출 열두 번이 되어 일일 한도를 태운다.

                    **빈 배열은 "공휴일이 없는 해" 라는 뜻이다.** 조회에 실패하면 빈 배열이 아니라 502 가 나간다 —
                    실패를 빈 목록으로 답하면 앱이 공휴일을 평일로 세어 연차를 과다 계산하기 때문이다.

                    공휴일은 연 단위로 미리 공표되고 확정되면 바뀌지 않으므로, 앱이 오래 캐시해도 된다.""")
    @ApiResponse(responseCode = "200", description = "조회 성공(공휴일이 없는 해면 빈 배열)")
    @ApiResponse(responseCode = "400", description = "year 가 정수가 아니거나 지난해~내년 범위 밖(LEAVE-015)")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "502", description = "공휴일(특일정보) 조회 실패로 목록을 만들 수 없음")
    ApiResponseBody<HolidayResponse> holidays(
            @Parameter(description = "조회할 연도. 지난해~내년만 허용", example = "2026") int year);
}
