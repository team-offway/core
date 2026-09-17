package com.offway.core.liveactivity.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.liveactivity.controller.dto.LiveActivityRegisterRequest;
import com.offway.core.liveactivity.controller.dto.PushToStartRegisterRequest;
import com.offway.core.liveactivity.controller.dto.PushToStartUnregisterRequest;
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

    @Operation(
            summary = "잠금화면 띄우기 토큰 등록 (push-to-start)",
            description =
                    """
                    **앱을 안 열어도** 서버가 이 기기 잠금화면에 여행 카드를 띄울 수 있게 하는 토큰이다
                    (iOS 17.2+, `Activity.pushToStartTokenUpdates`).

                    **위의 갱신 토큰과 다른 값이다.** 저쪽은 *이미 떠 있는 카드 하나*를 가리키고 (사용자,
                    코스) 단위인데, 이건 **그 기기**를 가리키고 카드를 한 번도 띄운 적이 없어도 나온다.
                    그래서 코스를 함께 보내지 않는다 — 무엇을 띄울지는 서버가 그날 정한다.

                    **앱 시작·로그인 직후마다 그냥 보내면 된다.** 같은 토큰이 다시 와도 200이고 행이
                    늘지 않는다(갱신 시각만 고쳐 쓴다). 앱을 지웠다 깔면 토큰이 바뀌므로 매번 보내는 것이
                    맞다.

                    **한 사람이 기기 둘이면 행도 둘이다.** 폰과 태블릿 양쪽 잠금화면에 카드가 뜬다.

                    **POST 가 아니라 PUT 인 이유** — 같은 요청을 몇 번 보내도 결과가 같다(멱등). 새로
                    만드는지 고쳐 쓰는지가 요청마다 달라 201 도 쓰지 않는다.

                    등록한다고 카드가 바로 뜨지는 않는다. 띄우는 것은 **낮에 도는 배치**이고, 출발 5일
                    이내이거나 여행 중인 코스가 있어야 한다.
                    """)
    @ApiResponse(responseCode = "200", description = "등록·갱신 성공")
    @ApiResponse(
            responseCode = "400",
            description = "token 누락·빈 값·512자 초과 · hex 가 아니거나 길이가 홀수")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "역할 없는 자격증명(Basic) — 소유자를 정할 수 없어 거절")
    ApiResponseBody<Void> registerPushToStart(UUID userId, PushToStartRegisterRequest request);

    @Operation(
            summary = "잠금화면 띄우기 토큰 해제 (push-to-start)",
            description =
                    """
                    **토큰을 함께 보내면 그 기기만** 해제한다. 로그아웃(`POST /api/v1/auth/logout`)이
                    이미 그렇게 갈린다 — refresh 를 실으면 그 기기만, 안 실으면 전부다. 여기서도 같은
                    기준을 쓴다. 폰에서 로그아웃했다고 태블릿 잠금화면의 카드까지 끊으면 사용자에게는
                    "아무것도 안 했는데 사라졌다" 로 보인다.

                    **본문을 비우면 이 사람의 모든 기기**가 해제된다. 계정을 지우기 전이나 "모든
                    기기에서 로그아웃" 에 쓴다.

                    **토큰을 경로가 아니라 본문으로 받는다.** 이 값을 아는 쪽은 그 기기 잠금화면에
                    카드를 만들 수 있어 비밀값에 준하는데, URL 에 실으면 프록시 접근 로그에 그대로
                    남는다. 본문은 안 남는다.

                    **앱이 이걸 안 불러도 남의 일정이 새지는 않는다.** 같은 기기에 다른 계정이 등록하면
                    서버가 앞 계정의 등록을 그때 정리한다 — 이 API 는 그보다 빨리 치우는 수단이다.

                    **지울 것이 없어도 성공(200)이다.** 원한 상태가 이미 이뤄져 있는데 404 를 띄울
                    이유가 없다.

                    이미 떠 있는 카드를 내리지는 않는다. 그건 앱이 하거나, 여행이 끝나면 자정 배치가
                    종료를 보낸다.
                    """)
    @ApiResponse(responseCode = "200", description = "해제 성공(지울 등록이 없어도 성공)")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "역할 없는 자격증명(Basic) — 소유자를 정할 수 없어 거절")
    ApiResponseBody<Void> unregisterPushToStart(UUID userId, PushToStartUnregisterRequest request);
}
