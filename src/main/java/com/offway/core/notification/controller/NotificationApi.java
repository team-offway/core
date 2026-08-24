package com.offway.core.notification.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.notification.controller.dto.NotificationsResponse;
import com.offway.core.notification.controller.dto.UnreadCountResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;

/**
 * 알림 API 문서 계약(#263). 매핑은 구현체({@link NotificationController})가 소유한다.
 *
 * <p><b>소유자는 파라미터가 아니다</b>(#280). 세 엔드포인트 모두 액세스 토큰이 정하며, 앱이 보내는 값으로는
 * 대상을 바꿀 수 없다.
 */
@Tag(name = "알림", description = "알림 목록 · 읽음 처리")
public interface NotificationApi {

    @Operation(
            summary = "알림 목록",
            description =
                    """
                    **로그인한 사용자**의 알림을 최근 것부터 준다. 안 읽은 개수(`unreadCount`)를 함께 주므로
                    홈 배지가 이 응답 하나로 채워진다.

                    **문구는 서버가 주지 않는다.** 각 알림은 종류(`type`)만 싣고, 아이콘과 문구는 앱이 그 값에
                    맞춘다. 문구를 서버에 굳혀 두면 이미 쌓인 알림이 옛 문구로 남아 화면에 두 세대가 섞인다.

                    현재 `type` 은 `TRIP_TOMORROW`(내일 여행)와 `TRIP_AFTER`(여행이 끝났다 — 연차를 기록해
                    달라) 둘이다. **값은 늘어날 수 있으므로 모르는 값은 무시하거나 기본 아이콘으로 그린다** —
                    앱을 업데이트하지 않은 사용자에게도 새 알림이 간다.

                    `courseId` 가 있으면 누를 때 그 코스로 이동한다. 없을 수도 있다(코스와 무관한 알림).
                    지워진 코스를 가리킬 수도 있다 — 알림은 코스가 사라져도 남는다.

                    **`unreadCount` 는 페이지와 무관한 전체 수다.** 이 페이지 안의 안읽음 수가 아니다.

                    **페이지로 끊어 준다.** 전체 건수·페이지 수는 응답 래퍼의 `pageResponse` 에 담긴다.
                    `size` 는 기본 20, 최대 100 이며 넘겨 보내면 거절하지 않고 100 으로 자른다.
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공(없으면 빈 목록). 페이지 정보는 pageResponse")
    @ApiResponse(responseCode = "400", description = "page·size 가 정수가 아님")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "역할 없는 자격증명(Basic) — 소유자를 정할 수 없어 거절")
    ApiResponseBody<NotificationsResponse> notifications(
            UUID userId,
            @Parameter(description = "0부터 시작하는 페이지 번호. 없으면 0, 음수는 0 으로 자른다", example = "0") Integer page,
            @Parameter(description = "페이지 크기. 없으면 20, 최대 100(초과분은 잘림)", example = "20") Integer size);

    @Operation(
            summary = "알림 하나 읽음",
            description =
                    """
                    알림 하나를 읽음으로 바꾸고 **남은 안읽음 개수**를 돌려준다. 배지를 고치려고 목록을 다시
                    부르지 않아도 된다.

                    **이미 읽은 알림에 다시 보내도 성공(200)이다.** 사용자가 원한 상태가 이미 이뤄져 있고,
                    알림 화면은 같은 요청을 두 번 보내기 쉬운 자리다. 처음 읽은 시각은 덮어쓰지 않는다.

                    **없는 알림과 남의 알림은 똑같이 404 다.** 남의 것에 403 을 주면 "그 id 는 존재한다" 를
                    알려주는 셈이라, id 를 훑어 남의 알림 존재를 확인할 수 있다.
                    """)
    @ApiResponse(responseCode = "200", description = "읽음 처리 성공(이미 읽었어도 성공)")
    @ApiResponse(responseCode = "400", description = "알림 ID 가 정수가 아님")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "역할 없는 자격증명(Basic) — 소유자를 정할 수 없어 거절")
    @ApiResponse(responseCode = "404", description = "요청한 알림이 없거나 소유자가 아님")
    ApiResponseBody<UnreadCountResponse> read(
            UUID userId, @Parameter(description = "알림 ID", example = "1") long notificationId);

    @Operation(
            summary = "전체 읽음",
            description =
                    """
                    로그인한 사용자의 안 읽은 알림을 한 번에 읽음 처리하고 **남은 안읽음 개수**를 돌려준다.

                    보통 0 이지만, 처리와 같은 순간에 새 알림이 들어왔다면 0 이 아닐 수 있다. 응답 값을
                    그대로 배지에 쓰면 된다.

                    **읽을 것이 없어도 성공(200)이다.**
                    """)
    @ApiResponse(responseCode = "200", description = "전체 읽음 처리 성공(읽을 것이 없어도 성공)")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "역할 없는 자격증명(Basic) — 소유자를 정할 수 없어 거절")
    ApiResponseBody<UnreadCountResponse> readAll(UUID userId);
}
