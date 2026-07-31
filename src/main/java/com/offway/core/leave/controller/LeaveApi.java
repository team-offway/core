package com.offway.core.leave.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.leave.controller.dto.AddLeaveUsageRequest;
import com.offway.core.leave.controller.dto.AvailableTimeRequest;
import com.offway.core.leave.controller.dto.AvailableTimeResponse;
import com.offway.core.leave.controller.dto.MyLeaveResponse;
import com.offway.core.leave.controller.dto.SandwichResponse;
import com.offway.core.leave.controller.dto.UpdateMyLeaveRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;

/** 연차·가용시간 API 문서 계약. 매핑·검증 어노테이션은 구현체({@link LeaveController})가 소유한다. */
@Tag(name = "연차", description = "연차 기반 가용시간(LNT)·샌드위치 연휴·내 연차")
public interface LeaveApi {

    @Operation(
            summary = "내 연차 조회",
            description = "총 연차·쓴 연차·남은 연차와 사용 내역. 아직 설정한 적이 없으면 총 0·내역 없음으로 답한다(404 아님).")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = "X-Guest-Id 헤더 누락 · 헤더가 비었거나 64자 초과")
    ApiResponseBody<MyLeaveResponse> myLeave(
            @Parameter(description = "소유 키 헤더", example = "guest-abc123") String guestId);

    @Operation(
            summary = "내 연차 수정",
            description = "총 연차를 고쳐 쓴다(와이어프레임 +/- 스테퍼). 0.5 단위로 반차 조합도 넣을 수 있다.")
    @ApiResponse(responseCode = "200", description = "수정 성공")
    @ApiResponse(
            responseCode = "400",
            description = "X-Guest-Id 헤더 누락·빈 값·64자 초과 · totalDays 누락 · 0.5 단위가 아니거나 0~365 범위 밖")
    ApiResponseBody<MyLeaveResponse> updateMyLeave(
            @Parameter(description = "소유 키 헤더", example = "guest-abc123") String guestId,
            UpdateMyLeaveRequest request);

    @Operation(
            summary = "연차 사용 내역 추가",
            description = """
                    연차를 쓰거나(양수) 되돌린(음수) 내역을 남긴다.

                    남은 연차가 부족해도 서버는 막지 않는다 — 프론트가 경고하고 사용자가 확인하면 진행한다(결정 #38).
                    그래서 남은 연차는 음수가 될 수 있다.""")
    @ApiResponse(responseCode = "201", description = "추가 성공")
    @ApiResponse(
            responseCode = "400",
            description = "X-Guest-Id 헤더 누락·빈 값·64자 초과 · usedOn·days 누락 또는 형식 오류 · days 가 0 이거나 0.5 단위가 아님")
    ApiResponseBody<MyLeaveResponse> addLeaveUsage(
            @Parameter(description = "소유 키 헤더", example = "guest-abc123") String guestId,
            AddLeaveUsageRequest request);

    @Operation(
            summary = "가용 시간(LNT) 산출",
            description = """
                    여행 구간을 두 가지 방식 중 하나로 지정해 여행일수·소모 연차·이동 한계를 산출한다.

                    ① 날짜 직접 — startDate + endDate
                    ② 기간스타일 — periodStyle + baseDate (+ WEEKEND 는 weekendBridge, CONNECTED 는 leaveDays)

                    ②는 기준일에서 가장 가까운 실제 구간을 서버가 해석해 확정한다. 어느 방식이든 응답에
                    확정된 startDate·endDate 가 함께 내려간다.""")
    @ApiResponse(responseCode = "200", description = "산출 성공")
    @ApiResponse(
            responseCode = "400",
            description = "날짜 형식 오류 · 날짜와 기간스타일을 함께 보냄 또는 둘 다 없음 · 종료일이 시작일보다 앞섬 · "
                    + "여행 구간이 2박 3일 초과 · 기간스타일에 기준일 누락 · WEEKEND 인데 브릿지 요일 누락 · "
                    + "CONNECTED 인데 연차 일수 누락 또는 2~3 범위 밖")
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
