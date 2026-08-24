package com.offway.core.device.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.device.controller.dto.DeviceRegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.UUID;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 푸시 토큰 API 문서 계약(#264). 매핑은 구현체({@link DeviceController})가 소유한다. */
@Tag(name = "기기", description = "푸시 토큰 등록 · 해제")
public interface DeviceApi {

    @Operation(
            summary = "푸시 토큰 등록·갱신",
            description =
                    """
                    이 기기로 알림을 받을 주소(FCM 토큰)를 등록한다. 앱 시작 시·토큰이 갱신됐을 때 보내면 된다.

                    **같은 토큰을 여러 번 보내도 행이 늘지 않는다.** 이미 등록한 토큰이면 플랫폼·갱신 시각만
                    고쳐 쓴다. 그래서 **몇 번을 보내도 결과가 같다** — 재시도해도 안전하고, 실패했는지 애매하면
                    그냥 다시 보내면 된다.

                    앱을 지웠다 깔아 게스트 ID 가 새로 발급된 뒤 같은 토큰으로 등록하면 **새 게스트의 토큰이
                    따로 생긴다.** 옛 게스트의 등록을 뺏어오지 않는다 — 그렇게 두면 남의 토큰을 아는 쪽이
                    그 기기의 알림을 가로챌 수 있기 때문이다. 남는 옛 등록은 발송 쪽에서 정리한다.

                    **201 이 아니라 200 이다.** 새로 만드는지 고쳐 쓰는지가 요청마다 달라, 만들었다고
                    단정할 수 없다.

                    등록한다고 알림이 바로 가지는 않는다. 보낼 알림을 만드는 것은 별도다.
                    """)
    @ApiResponse(responseCode = "200", description = "등록·갱신 성공")
    @ApiResponse(
            responseCode = "400",
            description = "게스트 ID 누락·빈 값·64자 초과 · token 누락·빈 값·512자 초과 · platform 누락 또는 IOS·ANDROID 가 아님")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    ApiResponseBody<Void> register(
            UUID userId,
            DeviceRegisterRequest request);

    @Operation(
            summary = "푸시 토큰 해제",
            description =
                    """
                    이 소유자의 푸시 토큰을 **전부** 지운다. 로그아웃하거나 알림을 끌 때 부른다.

                    토큰을 따로 받지 않는다 — 게스트 ID 는 설치마다 발급되므로 그 아래 토큰은 사실상 이
                    기기의 것이고, "이 사람에게 알림이 가지 않게" 가 두 화면(로그아웃·알림 끄기)이 원하는
                    바와 같다.

                    **지울 것이 없어도 성공(200)이다.** 원한 상태가 이미 이뤄져 있는데 404 를 띄울 이유가 없다.

                    응답 데이터는 없다(`data: null`). 이 API 는 204 를 쓰지 않는다.
                    """)
    @ApiResponse(responseCode = "200", description = "해제 성공(지울 토큰이 없어도 성공)")
    @ApiResponse(responseCode = "400", description = "게스트 ID 누락·빈 값·64자 초과")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    ApiResponseBody<Void> unregister(
            UUID userId);
}
