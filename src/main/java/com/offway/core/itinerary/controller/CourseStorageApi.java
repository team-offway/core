package com.offway.core.itinerary.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.itinerary.controller.dto.TripOutcomeRequest;
import com.offway.core.itinerary.controller.dto.PendingTripsResponse;
import com.offway.core.leave.controller.dto.MyLeaveResponse;
import com.offway.core.itinerary.domain.CourseScope;
import com.offway.core.itinerary.controller.dto.CourseResponse;
import com.offway.core.itinerary.controller.dto.CourseSaveRequest;
import com.offway.core.itinerary.controller.dto.CourseShareResponse;
import com.offway.core.itinerary.controller.dto.CourseSummaryResponse;
import com.offway.core.itinerary.controller.dto.CourseUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

/** 코스 저장·조회 API 문서 계약. 매핑은 구현체({@link CourseStorageController})가 소유한다. */
@Tag(name = "내 코스", description = "코스 저장 · 내 코스 목록·상세")
public interface CourseStorageApi {

    @Operation(
            summary = "코스 저장",
            description =
                    """
                    생성한 코스를 게스트의 '내 코스'로 저장한다.

                    응답에 `shareToken` 이 함께 실린다(#143). 공유 URL 은 `/c/{shareToken}` 이고,
                    받은 사람은 `GET /api/v1/public/courses/{shareToken}` 으로 인증 없이 볼 수 있다.
                    **토큰이 있다고 공개된 것은 아니다** — 링크를 넘겨야 비로소 남이 볼 수 있다.
                    """)
    @ApiResponse(responseCode = "201", description = "저장 성공")
    @ApiResponse(
            responseCode = "400",
            description = "게스트 ID 누락 · 코스 구성 오류(순서·좌표 등) · Day 날짜가 여행 시작일보다 앞서거나 기간을 넘음"
                    + " · 첫날 연차 단위가 목록에 없는 값(FULL_DAY·HALF_DAY·QUARTER_DAY)")
    ApiResponseBody<CourseResponse> save(
            @Parameter(description = "게스트 식별자", example = "guest-abc123") String guestId, CourseSaveRequest request);

    @Operation(
            summary = "공유 링크만 발급 (내 코스에 담지 않음)",
            description =
                    """
                    추천 결과 화면에서 **담지 않고 바로 공유**할 때 쓴다. 요청 본문은 저장(`POST /courses`)과 같고,
                    응답은 `shareToken` 하나다. 공유 URL 은 `/c/{shareToken}` 이고, 받은 사람은
                    `GET /api/v1/public/courses/{shareToken}` 으로 인증 없이 볼 수 있다.

                    **내 코스 목록·상세에 나오지 않는다.** 담은 것이 아니므로 주인 없이 보관하며, 그래서
                    `X-Guest-Id` 도 받지 않는다. 담으려면 저장 API 를 따로 부른다 — 그쪽 응답에도 토큰이 실린다.

                    **한 번 발급하면 되돌릴 수 없다.** 주인이 없어 삭제 API 로 지울 수 없으므로, 링크를 뿌리기 전에
                    누를 버튼이다. 담은 코스의 공유는 저장 API 로 가면 나중에 코스째 지울 수 있다.

                    같은 코스를 두 번 보내면 **링크가 두 개** 생긴다. 요청 본문만으로는 같은 코스인지 알 수 없어
                    멱등하게 만들 근거가 없다 — 담은 코스의 링크가 코스당 하나인 것과 다른 점이다.

                    구성 검증은 저장과 똑같다. 링크로 열리는 코스가 담은 코스보다 느슨할 이유가 없다.
                    """)
    @ApiResponse(responseCode = "201", description = "발급 성공")
    @ApiResponse(
            responseCode = "400",
            description = "코스 구성 오류(순서·좌표 등) · Day 날짜가 여행 시작일보다 앞서거나 기간을 넘음 · 출발지 위도·경도 중 하나만 보냄"
                    + " · 첫날 연차 단위가 목록에 없는 값(FULL_DAY·HALF_DAY·QUARTER_DAY)")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    ApiResponseBody<CourseShareResponse> share(CourseSaveRequest request);

    @Operation(
            summary = "내 코스 목록",
            description = """
                    게스트가 저장한 코스 요약. `scope` 로 보는 범위를 정한다.

                    - `UPCOMING` — 오늘 포함 이후 여행, **가까운 것부터**
                    - `PAST` — 지난 여행, 최근 것부터
                    - `ALL`(기본) — 전부, 저장한 순서(최근 저장이 위)

                    각 항목은 `dDay`(오늘 기준 남은 날, 지난 여행이면 음수)와 `leaveDeducted`(연차 차감 여부),
                    그리고 `shareToken`(공유 링크 토큰)을 함께 준다.

                    **`shareToken` 이 null 인 항목이 있을 수 있다** — 공유 링크가 아직 발급된 적 없는 코스다.
                    상세(`GET /courses/{courseId}`)를 한 번 열면 그 자리에서 발급돼 채워진다. 목록에서 발급하지
                    않는 이유는 페이지당 최대 100건이라, 조회 한 번이 그만큼의 쓰기가 되기 때문이다.

                    **여행 날짜 없이 저장된 코스는 `ALL` 에만 나온다** — 날짜가 없으면 다가오는 여행인지 지난 여행인지
                    판단할 근거가 없고, 아무 쪽에나 끼워 넣으면 화면이 조용히 거짓말을 한다.

                    **페이지로 끊어 준다.** 전체 건수·페이지 수는 응답 래퍼의 `pageResponse` 에 담긴다.
                    `size` 는 최대 100 이며, 넘겨 보내면 거절하지 않고 100 으로 자른다 — 목록이 통째로 비는 것보다 낫다.""")
    @ApiResponse(responseCode = "200", description = "조회 성공(없으면 빈 목록). 페이지 정보는 pageResponse")
    @ApiResponse(responseCode = "400", description = "게스트 ID 누락, scope 가 UPCOMING·PAST·ALL 이 아님, 또는 page·size 가 정수가 아님")
    ApiResponseBody<List<CourseSummaryResponse>> myCourses(
            @Parameter(description = "게스트 식별자", example = "guest-abc123") String guestId,
            @Parameter(description = "보는 범위", example = "UPCOMING") CourseScope scope,
            @Parameter(description = "0부터 시작하는 페이지 번호. 없으면 0, 음수는 0 으로 자른다", example = "0") Integer page,
            @Parameter(description = "페이지 크기. 없으면 20, 최대 100(초과분은 잘림)", example = "20") Integer size);

    @Operation(
            summary = "코스 상세",
            description = """
                    저장 코스의 날짜별 타임라인과 혜택.

                    응답에 `shareToken` 이 실린다. 아직 링크가 없는 코스면 **이 조회가 발급해서 내려준다** —
                    그래야 저장 응답을 놓친 코스(앱 재설치·기기 변경)도 공유할 수 있다. 코스당 한 번뿐이고
                    두 번째부터는 같은 토큰이 나간다.""")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = "게스트 ID 누락")
    @ApiResponse(responseCode = "404", description = "요청한 코스가 없거나 소유자가 아님")
    // 차감 정보(leaveDeducted·consumedLeaveDays)를 함께 준다(#317) — 목록을 병행 조회하지 않아도 된다.
    ApiResponseBody<CourseResponse> course(
            @Parameter(description = "게스트 식별자", example = "guest-abc123") String guestId,
            @Parameter(description = "코스 ID", example = "1") long courseId);

    @Operation(
            summary = "여행 날짜 수정",
            description = """
                    저장 코스의 여행 시작일을 옮긴다. 편집 시트의 "여행날짜 수정" 이다.

                    **연차 차감량을 서버가 다시 계산한다.** 날짜가 바뀌면 구간의 평일 수·공휴일이 달라져 깎을
                    연차가 달라진다. 이미 차감한 코스면 기존 내역을 새 값으로 갱신하고, 차감한 적 없는 코스는
                    연차를 건드리지 않는다. 첫날 연차 단위(종일·반차·반반차)는 코스를 만들 때 고른 값을 그대로 쓴다.

                    옮긴 구간이 주말·공휴일뿐이라 깎을 평일이 없으면 **차감량을 0 으로 갱신하고 내역은 남긴다**.
                    깎인 연차는 그만큼 돌아오고, 그 행은 "이 코스를 확정했다" 는 표식으로 남는다 — 지우면
                    날짜를 옮겼다는 이유로 확정이 조용히 풀린다.

                    **지난 날짜로는 옮길 수 없다.** 그 코스는 곧바로 "끝난 여행"이 돼 홈이 "다녀오셨나요?" 를
                    묻는데, 사용자는 방금 계획을 고쳤을 뿐이다. 반대로 **날짜를 놓친 코스를 앞으로 당기는 것은
                    된다** — 판단 대상은 옮겨갈 날짜다.

                    기간(travelDays)·이동수단은 바꾸지 않는다. 그것들이 바뀌면 코스 구성 자체를 다시 짜야 하므로
                    생성(`POST /courses/generate`)으로 간다.

                    응답은 상세 조회와 같은 모양이라, 새 날짜 기준의 요일·날씨가 함께 담겨 화면을 바로 다시 그릴 수 있다.
                    `shareToken` 도 상세와 같이 실린다.""")
    @ApiResponse(responseCode = "200", description = "수정 성공")
    @ApiResponse(
            responseCode = "400",
            description = "게스트 ID 누락 · travelDate 누락이거나 날짜 형식이 아님 · 지난 날짜로 옮기려 함")
    @ApiResponse(responseCode = "404", description = "코스가 없거나 소유자가 아님")
    @ApiResponse(responseCode = "502", description = "공휴일(특일정보) 조회에 실패해 차감량을 다시 계산할 수 없음")
    ApiResponseBody<CourseResponse> updateCourse(
            @Parameter(description = "게스트 식별자", example = "guest-abc123") String guestId,
            @Parameter(description = "코스 ID", example = "12") long courseId,
            CourseUpdateRequest request);

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
            summary = "코스 확정 취소 — 연차 되돌리기",
            description = """
                    코스는 그대로 두고 차감한 연차만 되돌린다. 여행 계획을 접었을 때 쓴다.

                    **멱등하다** — 차감된 적이 없어도 200 이다. "취소했다" 와 "원래 없었다" 는 사용자에게 같은 결과다.

                    코스 자체를 지우면(`DELETE /courses/{courseId}`) 차감도 함께 되돌아가므로 따로 부를 필요가 없다.

                    **차감하는 API 는 이제 없다**(#288). 연차는 `POST /courses/{courseId}/trip-outcome` 에
                    `VISITED` 로 답할 때만 깎인다 — 여행을 다녀왔다는 사실이 차감의 유일한 근거다. 예전에는
                    `POST /courses/{courseId}/leave-deduction` 이 여행 **전에** 깎아서, 취소한 여행의 연차가
                    묶인 채 남고 홈 모달도 뜨지 않았다.

                    차감 단위(종일·반차·반반차)도 묻지 않는다. **코스가 만들어질 때 이미 답한 값**을 쓴다.""")
    @ApiResponse(responseCode = "200", description = "취소 성공 (차감된 적이 없어도 200 — 현재 상태를 준다)")
    @ApiResponse(responseCode = "400", description = "X-Guest-Id 헤더 누락·형식 오류")
    @ApiResponse(responseCode = "404", description = "코스가 없거나 소유자가 아님")
    ApiResponseBody<MyLeaveResponse> cancelLeaveDeduction(String guestId, long courseId);

    @Operation(
            summary = "홈 모달 — 물어볼 지난 여행",
            description = """
                    홈 진입 시 한 번 부른다. **여행이 끝났고**(종료일 < 오늘) **아직 답하지 않은** 코스를 준다.
                    비어 있으면 모달을 띄우지 않는다.

                    종료 당일은 넣지 않는다 — 아직 여행 중일 수 있다. 내 코스 카드에서 이미 "연차 차감하기" 를
                    눌렀다면 그게 곧 "다녀왔다" 는 답이므로 함께 빠진다. 여행 날짜 없이 저장된 코스는 지났는지
                    알 수 없어 대상이 아니다.

                    **모달은 이 응답 하나로 완성된다** — 지역명·여행 날짜·차감될 연차·지도에 찍을 좌표까지 들어
                    있어 카드를 그리려고 코스 상세를 다시 부를 일이 없다.""")
    @ApiResponse(responseCode = "200", description = "조회 성공 (물어볼 여행이 없으면 trips 가 빈 배열)")
    @ApiResponse(responseCode = "400", description = "X-Guest-Id 헤더 누락·형식 오류")
    @ApiResponse(responseCode = "502", description = "공휴일 조회(특일정보) 실패로 차감될 연차를 계산할 수 없음")
    ApiResponseBody<PendingTripsResponse> pendingTrips(String guestId);

    @Operation(
            summary = "홈 모달 — 다녀왔는지 답하기",
            description = """
                    `VISITED` 면 **이 시점에 연차를 깎는다**. `NOT_VISITED` 면 깎지 않는다.

                    안 간 여행도 기록한다 — 기록하지 않으면 "아직 안 물어본 것" 과 구분되지 않아 홈을 열 때마다
                    다시 뜬다.

                    답한 뒤의 내 연차를 돌려주므로, 모달이 상단 "남은 연차" 를 바로 고쳐 그릴 수 있다.

                    **`pending-trips` 가 물어봤을 법한 여행만 답을 받는다.** 아직 끝나지 않았거나(종료 당일 포함)
                    이미 답한 여행은 409 다 — 조회 조건과 쓰기 조건이 어긋나면 모달이 묻지 않은 것에도 답이
                    들어와 연차가 잘못 움직인다.

                    **여기가 연차를 깎는 유일한 자리다**(#288). 여행을 다녀왔다는 사실이 차감의 근거이므로,
                    답하기 전에는 어떤 경로로도 깎이지 않는다.

                    **차감 단위(종일·반차·반반차)는 묻지 않는다.** 모달에 넣을 질문이 아니어서가 아니라
                    **물을 필요가 없어서**다 — 코스를 만들 때 이미 답한 값을 서버가 그대로 쓴다. 예전에는 이
                    자리가 종일로 고정돼, 반차로 짠 코스도 하루치가 깎였다(사용자가 0.5 를 더 잃었다).""")
    @ApiResponse(responseCode = "200", description = "기록 성공 (VISITED 면 차감 반영된 연차)")
    @ApiResponse(responseCode = "400", description = "X-Guest-Id 헤더 누락·형식 오류, outcome 누락·잘못된 값, 또는 여행 날짜 없이 저장된 코스")
    @ApiResponse(responseCode = "404", description = "코스가 없거나 소유자가 아님")
    @ApiResponse(
            responseCode = "409",
            description = "답할 수 없는 여행 — 아직 끝나지 않았거나(종료 당일 포함), 이미 답했거나, 내 코스 카드에서 이미 연차를 차감했음")
    @ApiResponse(responseCode = "502", description = "공휴일 조회(특일정보) 실패로 차감 일수를 계산할 수 없음")
    ApiResponseBody<MyLeaveResponse> answerTripOutcome(String guestId, long courseId, TripOutcomeRequest request);
}
