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

/**
 * 연차·가용시간 API 문서 계약. 매핑·검증 어노테이션은 구현체({@link LeaveController})가 소유한다.
 *
 * <p>여기 엔드포인트는 전부 인증 게이트 뒤에 있다({@code anyRequest().authenticated()}, #122) — 그래서 어느
 * 메서드든 401 이 도달 가능하고, 전수 문서화 대상이다. 한 메서드에만 적으면 나머지가 공개로 읽힌다.
 */
@Tag(name = "연차", description = "연차 기반 가용시간(LNT)·샌드위치 연휴·내 연차")
public interface LeaveApi {

    @Operation(
            summary = "내 연차 조회",
            description = "총 연차·쓴 연차·남은 연차와 사용 내역. 아직 설정한 적이 없으면 총 0·내역 없음으로 답한다(404 아님).")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = "X-Guest-Id 헤더 누락 · 헤더가 비었거나 64자 초과")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    ApiResponseBody<MyLeaveResponse> myLeave(
            @Parameter(description = "소유 키 헤더", example = "guest-abc123") String guestId);

    @Operation(
            summary = "내 연차 수정",
            description = "총 연차를 고쳐 쓴다(와이어프레임 +/- 스테퍼). 0.5 단위로 반차 조합도 넣을 수 있다.")
    @ApiResponse(responseCode = "200", description = "수정 성공")
    @ApiResponse(
            responseCode = "400",
            description = "X-Guest-Id 헤더 누락·빈 값·64자 초과 · totalDays 누락 · 0.5 단위가 아니거나 0~365 범위 밖")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    ApiResponseBody<MyLeaveResponse> updateMyLeave(
            @Parameter(description = "소유 키 헤더", example = "guest-abc123") String guestId,
            UpdateMyLeaveRequest request);

    @Operation(
            summary = "연차 사용 내역 추가",
            description = """
                    연차를 쓴(양수) 또는 되돌린(음수) 내역을 남긴다. days 는 0.5 단위다.

                    **되돌릴 때는 음수 등록 대신 내역 삭제 API 를 쓴다.** 음수 등록은 같은 요청이 두 번 들어오면
                    그만큼 더 상쇄돼 장부가 틀어진다 — 취소가 아니라 새 기록이기 때문이다. 지금은 아직 받지만
                    거절할 예정이다(#276). 새로 붙이는 화면은 삭제 API 를 쓴다.

                    남은 연차가 부족해도 서버는 막지 않는다 — 프론트가 경고하고 사용자가 확인하면 진행한다(결정 #38).
                    그래서 남은 연차는 음수가 될 수 있다.""")
    @ApiResponse(responseCode = "201", description = "추가 성공")
    @ApiResponse(
            responseCode = "400",
            description = "X-Guest-Id 헤더 누락·빈 값·64자 초과 · usedOn·days 누락 또는 형식 오류 · "
                    + "days 가 0 이거나 0.5 단위가 아니거나 절댓값이 99 초과(LEAVE-010)")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    ApiResponseBody<MyLeaveResponse> addLeaveUsage(
            @Parameter(description = "소유 키 헤더", example = "guest-abc123") String guestId,
            AddLeaveUsageRequest request);

    @Operation(
            summary = "연차 사용 내역 삭제",
            description = """
                    사용 내역 한 건을 지우고 갱신된 내 연차 전체(총·쓴·남은 + 내역 목록)를 돌려준다 —
                    화면이 한 번의 왕복으로 다시 그린다.

                    코스 확정으로 기록된 내역(courseId 가 있는 것)은 여기서 지울 수 없다(409). 그 행은 차감량이자
                    확정 표식이라, 지우면 코스는 확정인데 연차는 안 깎인 상태가 남는다. 코스의 차감 취소로 되돌린다.

                    없는 내역과 남의 내역을 같은 404 로 답한다 — 번호를 넣어보며 존재 여부를 알아낼 수 없게 한다.""")
    @ApiResponse(responseCode = "200", description = "삭제 성공")
    @ApiResponse(responseCode = "400", description = "X-Guest-Id 헤더 누락·빈 값·64자 초과 · usageId 가 숫자가 아님")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "404", description = "그 내역이 없거나 다른 소유자의 것")
    @ApiResponse(responseCode = "409", description = "코스 확정으로 기록된 내역이라 연차 화면에서 지울 수 없음")
    ApiResponseBody<MyLeaveResponse> deleteLeaveUsage(
            @Parameter(description = "소유 키 헤더", example = "guest-abc123") String guestId,
            @Parameter(description = "지울 사용 내역 ID", example = "42") long usageId);

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
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "502", description = "공휴일 정보(특일정보) 조회 실패")
    ApiResponseBody<AvailableTimeResponse> availableTime(AvailableTimeRequest request);

    @Operation(summary = "샌드위치 연휴 추천", description = "조회 기간 안에서 최소 연차로 최대 휴식이 되는 황금 연차를 효율 순으로 추천한다.")
    @ApiResponse(responseCode = "200", description = "추천 성공 (없으면 빈 목록)")
    @ApiResponse(responseCode = "400", description = "fromDate 누락·형식 오류 · 조회 개월 수가 1~12 범위 밖")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "502", description = "공휴일 정보(특일정보) 조회 실패")
    ApiResponseBody<SandwichResponse> sandwich(
            @Parameter(description = "조회 시작일", example = "2026-05-01") LocalDate fromDate,
            @Parameter(description = "조회 개월 수 (1~12, 기본 6)", example = "6") int months,
            @Parameter(description = "남은 연차 (있으면 그 이하로 가능한 연휴만)", example = "8") Double remainingLeave);
}
