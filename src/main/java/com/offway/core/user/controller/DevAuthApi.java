package com.offway.core.user.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.user.controller.dto.DevLoginRequest;
import com.offway.core.user.controller.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 개발용 인증 API 문서 계약. 매핑은 구현체({@link DevAuthController})가 소유한다. */
@Tag(name = "인증(개발용)", description = "local 프로파일에서만 노출된다")
public interface DevAuthApi {

    @Operation(
            summary = "개발용 로그인",
            description =
                    "provider 검증 없이 사용자를 만들고 토큰을 발급한다. local 프로파일 전용이라 prod 에는 이 빈이 존재하지 않아 경로 자체가 열리지 않는다.")
    @ApiResponse(responseCode = "200", description = "발급 성공")
    ApiResponseBody<TokenResponse> devLogin(DevLoginRequest request);
}
