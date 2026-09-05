package com.offway.core.user.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.user.controller.dto.LogoutRequest;
import com.offway.core.user.controller.dto.ReissueRequest;
import com.offway.core.user.controller.dto.SocialLoginRequest;
import com.offway.core.user.controller.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;

/** 인증 API 문서 계약. 매핑은 구현체({@link AuthController})가 소유한다. */
@Tag(name = "인증", description = "소셜 로그인 · 토큰 재발급 · 로그아웃")
public interface AuthApi {

    @Operation(
            summary = "소셜 로그인 콜백",
            description =
                    """
                    앱이 provider SDK 로 받은 토큰을 검증하고 서비스 토큰을 발급한다. 처음 보는 신원이면 그대로 가입되고
                    `isNewUser` 가 true 로 내려간다 — 앱은 이 값으로 온보딩(잔여 연차 입력)과 홈을 가른다.

                    provider 별로 넘길 토큰과 서버의 확인 방식이 다르다.
                    - `kakao` — 액세스 토큰. 서버가 카카오 프로필 API 를 조회해 회원번호를 확인한다
                    - `apple` — identityToken(JWT). Apple 공개키로 서명과 aud 를 검증한다
                    - `google` — idToken(JWT). Google 공개키로 서명과 aud('웹' 클라이언트 ID)를 검증한다

                    `email`·`name` 은 Apple 이 최초 로그인 응답에만 주므로 그때만 실린다. `providerUserId` 는 받지만
                    신원 판단에는 쓰지 않는다 — 서버가 provider 에게 직접 확인한 식별자만 믿는다.

                    인증 없이 호출할 수 있다(토큰을 받으러 오는 경로다).
                    """)
    @ApiResponse(responseCode = "200", description = "로그인 성공(신규 가입 포함)")
    @ApiResponse(responseCode = "400", description = "토큰 누락 · 지원하지 않는 provider 경로값(USER-002)")
    @ApiResponse(
            responseCode = "401",
            description = "토큰이 무효 — 서명·만료·issuer/audience 불일치, 카카오가 액세스 토큰을 거부(USER-001)")
    @ApiResponse(
            responseCode = "502",
            description = "provider 를 부르지 못함 — 공개키(JWKS) 조회 실패 · 카카오 프로필 API 실패·타임아웃(USER-005)")
    ApiResponseBody<TokenResponse> callback(
            @Parameter(description = "소셜 provider — kakao · apple · google (대소문자 무관)", example = "kakao")
                    String provider,
            SocialLoginRequest request);

    @Operation(
            summary = "토큰 재발급",
            description =
                    """
                    refresh 토큰을 회전시켜 새 토큰 쌍을 발급한다. 응답의 `isNewUser` 는 항상 false 다.

                    <b>방금 회전된 refresh 를 곧바로 다시 보내면(10초 안) 거절하지 않고 새 쌍을 다시 준다.</b>
                    앞선 응답이 유실됐을 때(타임아웃 · 재배포 중 연결 끊김) 앱에는 받아 둔 새 토큰이 없어서,
                    거절하면 앱은 만료와 구분하지 못하고 로그아웃한다. 이 창을 벗어난 뒤 오면 탈취로 보고
                    그 사용자의 토큰을 모두 폐기한다.

                    <b>로그아웃한 토큰을 다시 보내는 것은 탈취로 보지 않는다.</b> 401 로 거절만 하고 남은 기기는
                    끊지 않는다 — 사용자가 스스로 끝낸 세션이라 경보를 울릴 근거가 아니다.

                    인증 없이 호출할 수 있다(access 가 만료됐을 때 부르는 경로다).
                    """)
    @ApiResponse(responseCode = "200", description = "재발급 성공 — 회전 직후 10초 안의 재시도 포함")
    @ApiResponse(responseCode = "400", description = "refresh 토큰 누락")
    @ApiResponse(responseCode = "401", description = "refresh 토큰이 없거나 만료·폐기됨(USER-003)")
    ApiResponseBody<TokenResponse> reissue(ReissueRequest request);

    @Operation(
            summary = "로그아웃",
            description =
                    """
                    <b>이 기기만</b> 로그아웃한다 — 본문에 그 기기의 refresh 토큰을 실으면 그 세션 하나만 폐기한다.

                    본문을 비우거나 아예 보내지 않으면 <b>이 사용자의 모든 기기</b>가 끊긴다(옛 앱 호환 · 전체 로그아웃).

                    이미 발급된 access 토큰은 만료(기본 1시간)까지 유효하다 — 무상태 JWT 라 서버가 되불러올 수 없다.

                    보낸 refresh 가 없거나 이미 폐기됐거나 다른 사용자 것이어도 <b>200</b> 이다. 그 기기의 로컬
                    토큰은 어차피 지워지고, 실패로 돌려주면 앱이 "로그아웃이 안 됐다" 로 읽는다.
                    """)
    @ApiResponse(responseCode = "200", description = "로그아웃 성공 — 폐기할 세션을 못 찾은 경우도 포함")
    @ApiResponse(responseCode = "401", description = "access 토큰이 없거나 무효·만료(USER-004) · 자격증명 없음(COMMON-401)")
    @ApiResponse(responseCode = "403", description = "역할 없는 자격증명(Basic) — 소유자를 정할 수 없어 거절")
    ApiResponseBody<Void> logout(UUID userId, LogoutRequest request);
}
