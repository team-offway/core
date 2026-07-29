package com.offway.core.user.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.user.controller.dto.LoginRequest;
import com.offway.core.user.controller.dto.ReissueRequest;
import com.offway.core.user.controller.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;

/** 인증 API 문서 계약. 매핑은 구현체({@link AuthController})가 소유한다. */
@Tag(name = "인증", description = "OAuth 로그인 · 토큰 재발급 · 로그아웃")
public interface AuthApi {

    @Operation(
            summary = "OAuth 로그인",
            description = "앱이 provider SDK 로 받은 ID 토큰을 검증하고 서비스 토큰을 발급한다. 처음 보는 신원이면 그대로 가입된다.")
    @ApiResponse(responseCode = "200", description = "로그인 성공(신규 가입 포함)")
    @ApiResponse(responseCode = "400", description = "provider 누락·미지원 · ID 토큰 누락")
    @ApiResponse(responseCode = "401", description = "ID 토큰 서명·만료·issuer/audience 불일치")
    @ApiResponse(responseCode = "502", description = "provider 공개키(JWKS) 조회 실패")
    ApiResponseBody<TokenResponse> login(LoginRequest request);

    @Operation(
            summary = "토큰 재발급",
            description = "refresh 토큰을 회전시켜 새 토큰 쌍을 발급한다. 이미 사용된 refresh 를 다시 보내면 탈취로 보고 해당 사용자의 토큰을 모두 폐기한다.")
    @ApiResponse(responseCode = "200", description = "재발급 성공")
    @ApiResponse(responseCode = "400", description = "refresh 토큰 누락")
    @ApiResponse(responseCode = "401", description = "refresh 토큰이 없거나 만료·폐기됨")
    ApiResponseBody<TokenResponse> reissue(ReissueRequest request);

    @Operation(
            summary = "로그아웃",
            description = "이 사용자의 refresh 토큰을 모두 폐기한다. 이미 발급된 access 토큰은 만료(기본 1시간)까지 유효하다.")
    @ApiResponse(responseCode = "200", description = "로그아웃 성공")
    @ApiResponse(responseCode = "401", description = "access 토큰이 없거나 무효·만료")
    ApiResponseBody<Void> logout(UUID userId);
}
