package com.offway.core.liveactivity.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.liveactivity.controller.dto.LiveActivityRegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;

/** 잠금화면(Live Activity) 토큰 API 문서 계약(#575). 매핑은 구현체({@link LiveActivityController})가 소유한다. */
@Tag(name = "잠금화면", description = "여행 D-day Live Activity 갱신 토큰 등록 · 해제")
public interface LiveActivityApi {

    @Operation(
            summary = "잠금화면 갱신 토큰 등록·갱신",
            description =
                    """
                    잠금화면·다이나믹 아일랜드에 띄운 여행 카드를 **서버가 매일 자정에 갱신**할 수 있게
                    그 카드의 push token 을 등록한다.

                    **기기 토큰(`/api/v1/devices`)과 다른 값이다.** 저쪽은 기기 하나를 가리키고 앱을 지울
                    때까지 살지만, 이 토큰은 **잠금화면에 띄운 카드 하나**를 가리키고 그 카드가 사라지면
                    함께 죽는다. 같은 기기에서 코스 둘을 띄우면 토큰도 둘이다.

                    **같은 코스로 여러 번 보내도 행이 늘지 않는다.** (사용자, 코스)로 한 행만 두고 토큰만
                    갈아 끼운다 — **몇 번을 보내도 결과가 같다.** 네트워크가 끊겨 성공했는지 애매하면 그냥
                    다시 보내면 되고, iOS 가 토큰을 갱신해 줄 때(`pushTokenUpdates`)도 같은 요청을 다시
                    보내면 된다.

                    **자기 코스여야 한다.** 이 등록의 대가로 서버가 그 코스의 여행지·날짜를 매일 잠금화면에
                    그려 주므로, 남의 코스 id 로는 등록되지 않는다(404).

                    **201 이 아니라 200 이다.** 새로 만드는지 고쳐 쓰는지가 요청마다 달라 만들었다고
                    단정할 수 없다.

                    등록 뒤 첫 갱신은 **다음 자정**이다. 즉시 한 번 쏘지 않는다 — 앱이 방금 띄운 카드라
                    이미 오늘 값이 들어 있다.
                    """)
    @ApiResponse(responseCode = "200", description = "등록·갱신 성공")
    @ApiResponse(
            responseCode = "400",
            description = "courseId 누락·0 이하 · pushToken 누락·빈 값·512자 초과")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "역할 없는 자격증명(Basic) — 소유자를 정할 수 없어 거절")
    @ApiResponse(responseCode = "404", description = "그 코스가 없거나 내 코스가 아님")
    ApiResponseBody<Void> register(UUID userId, LiveActivityRegisterRequest request);

    @Operation(
            summary = "잠금화면 갱신 토큰 해제",
            description =
                    """
                    이 코스의 등록을 지운다. 사용자가 카드를 내렸거나 앱이 Activity 를 끝냈을 때 부른다.

                    **지울 것이 없어도 성공(200)이다.** 원한 상태가 이미 이뤄져 있는데 404 를 띄울 이유가 없다.

                    **남의 코스 id 를 적어도 아무 일이 없다.** 지우는 범위가 로그인한 사용자의 행으로 이미
                    좁혀져 있다.

                    **안 불러도 결국 정리된다.** 여행이 끝나면 자정 배치가 종료를 보내고 행을 지우고,
                    토큰이 죽으면(iOS 는 카드를 최대 8시간 뒤 스스로 끝낸다) 발송이 `410` 을 받아 지운다.
                    이 API 는 그보다 빨리 치우고 싶을 때 쓴다.

                    응답 데이터는 없다(`data: null`). 이 API 는 204 를 쓰지 않는다.
                    """)
    @ApiResponse(responseCode = "200", description = "해제 성공(지울 등록이 없어도 성공)")
    @ApiResponse(responseCode = "400", description = "courseId 가 0 이하")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "역할 없는 자격증명(Basic) — 소유자를 정할 수 없어 거절")
    ApiResponseBody<Void> unregister(
            UUID userId,
            @Parameter(description = "해제할 코스 id", example = "122") Long courseId);
}
