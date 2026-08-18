package com.offway.core.user.controller.dto;

import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.service.dto.SocialLoginCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 소셜 로그인 요청 — 앱이 provider SDK 로 받아 온 토큰을 넘긴다. provider 는 경로 변수로 온다.
 *
 * <p><b>{@code accessToken} 에 담기는 것이 provider 마다 다르다.</b> 필드 이름을 하나로 둔 것은 앱이 provider 별로
 * 다른 본문을 만들지 않게 하기 위해서고, 서버는 provider 를 알고 있으므로 무엇이 왔는지 헷갈리지 않는다.
 *
 * <table>
 *   <tr><th>provider</th><th>accessToken 에 담기는 것</th><th>서버가 하는 일</th></tr>
 *   <tr><td>kakao</td><td>액세스 토큰</td><td>프로필 API 조회로 회원번호 확인</td></tr>
 *   <tr><td>apple</td><td>identityToken(JWT)</td><td>Apple 공개키로 서명·{@code aud} 검증</td></tr>
 *   <tr><td>google</td><td>idToken(JWT)</td><td>Google 공개키로 서명·{@code aud} 검증</td></tr>
 * </table>
 *
 * @param accessToken provider SDK 가 발급한 토큰
 * @param email 표시용 이메일(선택). Apple 은 최초 로그인 응답에만 주므로 그때 받아 넘기지 않으면 영영 얻을 수 없다
 * @param name 표시 이름(선택). 위와 같은 이유로 Apple 최초 로그인에서만 값이 온다
 * @param authorizationCode Apple 이 로그인 응답에 함께 주는 1회용 코드(#287). 탈퇴 시 Apple 연결을 끊는 데
 *     필요하다 — 이 값이 없으면 우리 DB 만 지워지고 Apple '이 App으로 로그인' 목록에는 남는다. 카카오·구글은
 *     보내지 않고, 안 보내도 로그인은 지금과 똑같다
 * @param providerUserId <b>받지만 신원 판단에 쓰지 않는다.</b> 앱 편의를 위해 계약에 남겨 둔 필드다. 이 값을 믿고
 *     계정을 찾으면 아무나 남의 식별자를 적어 그 계정으로 로그인할 수 있다 — 요청 한 번짜리 계정 탈취가 된다.
 *     식별자는 언제나 서버가 provider 에게서 직접 확인한 값을 쓴다
 */
public record SocialLoginRequest(
        @NotBlank @Schema(description = "provider SDK 가 발급한 토큰 (kakao=액세스 토큰, apple/google=ID 토큰)")
                String accessToken,
        @Schema(description = "이메일(선택). Apple 최초 로그인에서만 온다", example = "user@example.com", nullable = true)
                String email,
        @Schema(description = "표시 이름(선택). Apple 최초 로그인에서만 온다", example = "홍길동", nullable = true)
                String name,
        @Schema(
                        description = "제공자별 사용자 식별자(선택). 서버는 신원 판단에 쓰지 않고 provider 에게 직접 확인한다",
                        nullable = true)
                String providerUserId,
        @Schema(
                        description = "Apple 이 준 1회용 authorization code (Apple 만). 탈퇴 시 연결 해제에 쓴다 "
                                + "— 안 보내면 해제만 못 하고 로그인은 그대로다",
                        nullable = true)
                String authorizationCode) {

    /**
     * @param guestId 이 기기의 게스트 키(#34). 본문이 아니라 헤더로 온다 — 코스·연차가 쓰는 그 값과 같은 것이라
     *     계약을 둘로 만들지 않는다. 안 보내는 클라이언트도 있어 null 일 수 있다
     */
    public SocialLoginCommand toCommand(String provider, String guestId) {
        return new SocialLoginCommand(
                AuthProvider.from(provider), accessToken, name, email, guestId, authorizationCode);
    }
}
