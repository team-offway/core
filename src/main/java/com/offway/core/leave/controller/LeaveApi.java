package com.offway.core.leave.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.leave.controller.dto.AddLeaveUsageRequest;
import com.offway.core.leave.controller.dto.AvailableTimeRequest;
import com.offway.core.leave.controller.dto.AvailableTimeResponse;
import com.offway.core.leave.controller.dto.MyLeaveResponse;
import com.offway.core.leave.controller.dto.SandwichResponse;
import com.offway.core.leave.controller.dto.UpdateLeaveUsageRequest;
import com.offway.core.leave.controller.dto.UpdateMyLeaveRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 연차·가용시간 API 문서 계약. 매핑·검증 어노테이션은 구현체({@link LeaveController})가 소유한다.
 *
 * <p>여기 엔드포인트는 전부 인증 게이트 뒤에 있어(#122) 어느 메서드든 401 이 도달 가능하고, 전수 문서화
 * 대상이다. 한 메서드에만 적으면 나머지가 공개로 읽힌다.
 *
 * <p><b>다만 요구하는 인증의 종류가 갈린다</b>(#280). {@code /me} 계열은 소유자가 있는 데이터라
 * {@code ROLE_USER} 를 요구하고({@code SecurityConfig.USER_OWNED_PATHS}), 나머지(가용시간·샌드위치)는
 * 계산만 하므로 인증되기만 하면 된다. 그래서 <b>{@code /me} 계열만 403 이 도달 가능하다</b> — 팀·Swagger·
 * 스모크가 쓰는 Basic 자격증명으로 부르면 인증은 통과하지만 역할이 없어 거기서 끊긴다.
 *
 * <p><b>"내 연차" 의 주인은 access 토큰이 정한다</b>(#280). 예전에는 {@code X-Guest-Id} 헤더가 소유자였는데,
 * 서버가 검증할 수 없는 문자열이라 그 값을 아는 것만으로 남의 연차를 읽고 지울 수 있었다. 이제 소유 키를 요청이
 * 정하지 않으므로 <b>그 헤더는 보내지 않는다</b>(보내도 무시된다) — 헤더 형식 때문에 400 이 나던 사유도 함께 사라졌다.
 */
@Tag(name = "연차", description = "연차 기반 가용시간(LNT)·샌드위치 연휴·내 연차")
public interface LeaveApi {

    @Operation(
            summary = "내 연차 조회",
            description = "총 연차·쓴 연차·남은 연차와 사용 내역. 아직 설정한 적이 없으면 총 0·내역 없음으로 답한다(404 아님).")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "역할 없는 자격증명(Basic) — 소유자를 정할 수 없어 거절")
    ApiResponseBody<MyLeaveResponse> myLeave(UUID userId);

    @Operation(
            summary = "내 연차 수정",
            description = "총 연차를 고쳐 쓴다(와이어프레임 +/- 스테퍼). 0.25 단위로 반차·반반차 조합도 넣을 수 있다.")
    @ApiResponse(responseCode = "200", description = "수정 성공")
    @ApiResponse(
            responseCode = "400",
            description = "totalDays 누락 · 0.25 단위가 아니거나 0~99 범위 밖")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "역할 없는 자격증명(Basic) — 소유자를 정할 수 없어 거절")
    ApiResponseBody<MyLeaveResponse> updateMyLeave(UUID userId, UpdateMyLeaveRequest request);

    @Operation(
            summary = "연차 사용 내역 추가",
            description = """
                    연차를 쓴 내역을 남긴다. days 는 0.25 단위 양수다.

                    되돌릴 때는 음수를 등록하지 않는다 — 내역 삭제 API 를 쓴다. 음수 등록은 같은 요청이 두 번
                    들어오면 그만큼 더 상쇄돼 남은 연차가 총 연차를 넘었다(LEAVE-013 으로 거절한다).

                    남은 연차가 부족해도 서버는 막지 않는다 — 프론트가 경고하고 사용자가 확인하면 진행한다(결정 #38).
                    그래서 남은 연차는 음수가 될 수 있다.""")
    @ApiResponse(responseCode = "201", description = "추가 성공")
    @ApiResponse(
            responseCode = "400",
            description = "usedOn·days 누락 또는 형식 오류 · "
                    + "days 가 0 이거나 0.25 단위가 아니거나 99 초과(LEAVE-010) · days 가 음수(LEAVE-013)")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "역할 없는 자격증명(Basic) — 소유자를 정할 수 없어 거절")
    ApiResponseBody<MyLeaveResponse> addLeaveUsage(UUID userId, AddLeaveUsageRequest request);

    @Operation(
            summary = "연차 사용 내역 수정",
            description = """
                    사용 내역 한 건을 고치고 갱신된 내 연차 전체(총·쓴·남은 + 내역 목록)를 돌려준다 —
                    삭제와 같은 응답이라 화면이 한 번의 왕복으로 다시 그린다.

                    **보낸 필드만 바뀐다.** 안 보낸 필드는 그대로 둔다. 세 필드 모두 선택이고, 아무것도 안 보내면
                    아무것도 바뀌지 않은 채 현재 상태가 나간다.

                    **사유를 지우려면 빈 문자열(`""`)을 보낸다.** 필드를 빼거나 null 을 보내는 것은 "그대로 두라" 는
                    뜻이라 지우는 신호로 쓸 수 없다. 날짜·일수는 지울 수 있는 값이 아니므로 이 구분이 필요 없다 —
                    없애고 싶으면 내역 자체를 삭제한다.

                    **값 규칙은 등록과 같다.** days 는 0.25 단위 양수이고, 음수는 상쇄 등록이라 따로 거절한다
                    (되돌리려면 삭제 API 를 쓴다).

                    코스 확정으로 기록된 내역(courseId 가 있는 것)은 여기서 고칠 수 없다(409). 삭제와 같은 이유이고,
                    수정은 더 위험하다 — 일수를 고치면 코스가 아는 차감량과 조용히 어긋난다.

                    없는 내역과 남의 내역을 같은 404 로 답한다.""")
    @ApiResponse(responseCode = "200", description = "수정 성공")
    @ApiResponse(
            responseCode = "400",
            description = "usageId 가 숫자가 아님 · usedOn 날짜 형식 오류 · "
                    + "days 가 0 이거나 0.25 단위가 아니거나 99 초과(LEAVE-010) · days 가 음수(LEAVE-013)")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "404", description = "그 내역이 없거나 다른 소유자의 것")
    @ApiResponse(responseCode = "409", description = "코스 확정으로 기록된 내역이라 연차 화면에서 고칠 수 없음")
    ApiResponseBody<MyLeaveResponse> updateLeaveUsage(
            UUID userId,
            @Parameter(description = "고칠 사용 내역 ID", example = "42") long usageId,
            UpdateLeaveUsageRequest request);

    @Operation(
            summary = "연차 사용 내역 삭제",
            description = """
                    사용 내역 한 건을 지우고 갱신된 내 연차 전체(총·쓴·남은 + 내역 목록)를 돌려준다 —
                    화면이 한 번의 왕복으로 다시 그린다.

                    코스 확정으로 기록된 내역(courseId 가 있는 것)은 여기서 지울 수 없다(409). 그 행은 차감량이자
                    확정 표식이라, 지우면 코스는 확정인데 연차는 안 깎인 상태가 남는다. 코스의 차감 취소로 되돌린다.

                    없는 내역과 남의 내역을 같은 404 로 답한다 — 번호를 넣어보며 존재 여부를 알아낼 수 없게 한다.""")
    @ApiResponse(responseCode = "200", description = "삭제 성공")
    @ApiResponse(responseCode = "400", description = "usageId 가 숫자가 아님")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "역할 없는 자격증명(Basic) — 소유자를 정할 수 없어 거절")
    @ApiResponse(responseCode = "404", description = "그 내역이 없거나 다른 소유자의 것")
    @ApiResponse(responseCode = "409", description = "코스 확정으로 기록된 내역이라 연차 화면에서 지울 수 없음")
    ApiResponseBody<MyLeaveResponse> deleteLeaveUsage(
            UUID userId, @Parameter(description = "지울 사용 내역 ID", example = "42") long usageId);

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
                    + "CONNECTED 인데 연차 일수 누락 또는 2~3 범위 밖"
                    + " · 첫날 연차 단위가 목록에 없는 값(FULL_DAY·HALF_DAY·QUARTER_DAY)")
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
