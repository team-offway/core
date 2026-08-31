package com.offway.core.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

/**
 * 브라우저 로그인(#343) — 백오피스 화면이 쓴다. <b>앱은 쓰지 않는다.</b>
 *
 * <p>앱은 SDK 가 받아 온 토큰을 {@code POST /api/v1/auth/callback/{provider}} 로 넘긴다. 여기는 SDK 가 없는
 * 브라우저를 위한 경로라, 인가 코드 받기와 토큰 교환을 서버가 대신한다.
 */
@Tag(name = "브라우저 로그인")
public interface WebOAuthApi {

    @Operation(
            summary = "카카오 로그인 시작",
            description =
                    """
                    브라우저를 카카오 동의 화면으로 보낸다. 사람이 주소창으로 여는 경로라 <b>본문이 없다</b> —
                    응답은 언제나 `302` 이고, 볼 것은 `Location` 헤더다.

                    이 왕복을 잇는 1회용 값(`state`)을 만들어 **쿠키와 카카오 양쪽에** 둔다. 콜백에서 둘이
                    같은지 대조해, 남이 만든 인가 코드로 우리 어드민을 로그인시키는 것을 막는다.

                    카카오 설정(REST API 키·콜백 주소)이 없으면 카카오로 보내지 않고 백오피스 화면으로
                    되돌린다 — 사람이 동의까지 마치고 마지막에 실패하는 것보다 낫다.

                    인증 없이 호출할 수 있다(토큰을 받으러 오는 경로다).
                    """)
    @ApiResponse(
            responseCode = "302",
            description = "카카오 동의 화면으로 이동. 설정이 없으면 백오피스 화면으로 되돌린다")
    ResponseEntity<Void> start();

    @Operation(
            summary = "카카오 로그인 콜백",
            description =
                    """
                    카카오가 사용자를 되돌려 보내는 자리다. **카카오 개발자 콘솔에 등록한 Redirect URI 가
                    이 경로여야 한다.**

                    인가 코드를 액세스 토큰으로 바꾼 뒤, 앱 로그인과 <b>같은</b> 신원 확인·발급 경로를 탄다.
                    그래서 같은 사람이 앱으로 들어오든 여기로 들어오든 회원번호가 같다.

                    성공·실패 모두 `302` 로 백오피스 화면(`/admin/`)으로 되돌린다. 결과는 **URL 프래그먼트**로
                    전한다 — 쿼리스트링으로 넘기면 액세스 토큰이 서버 접근 로그와 리퍼러에 그대로 박힌다.

                    | 결과 | 프래그먼트 |
                    |---|---|
                    | 성공 | `#access_token=...&expires_in=...` |
                    | 동의 취소·코드 없음 | `#error=denied` |
                    | `state` 불일치(쿠키 만료 · 위조) | `#error=invalid_state` |
                    | 카카오가 코드를 거절 | `#error=rejected` |
                    | 카카오를 못 부름(타임아웃·5xx) | `#error=unavailable` |
                    | 카카오 설정 없음 | `#error=not_configured` |

                    발급되는 토큰에 어드민 역할이 실릴지는 화이트리스트(`admin_account`)가 정한다. 어드민이
                    아니어도 로그인 자체는 성공하고, 백오피스 API 만 `403` 이 된다.

                    인증 없이 호출할 수 있다(토큰을 받으러 오는 경로다).
                    """)
    @ApiResponse(responseCode = "302", description = "백오피스 화면으로 되돌림. 결과는 프래그먼트에 실린다")
    ResponseEntity<Void> callback(
            @Parameter(description = "카카오가 발급한 1회용 인가 코드. 사용자가 취소하면 오지 않는다")
                    String code,
            @Parameter(description = "로그인 시작 때 우리가 만든 값. 카카오가 그대로 돌려준다") String state,
            @Parameter(description = "카카오가 실어 보내는 실패 사유. 사용자가 동의를 취소하면 채워진다")
                    String error,
            @Parameter(hidden = true) String stateCookie);
}
