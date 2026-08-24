package com.offway.core.user.service.dto;

import com.offway.core.user.domain.AuthProvider;
import lombok.Builder;

/**
 * 소셜 로그인 커맨드(내부용).
 *
 * <p><b>조립이라 빌더다.</b> 문자열 넷이 줄지어 있고 요청 DTO 의 필드 순서와도 어긋나 있다
 * ({@code SocialLoginRequest} 는 email·name 순, 여기는 nickname·email 순) — 위치 인수로 넘기면
 * 두 값이 뒤바뀌어도 컴파일이 통과하고, 사용자에게는 이름 자리에 이메일이 뜬다.
 *
 * @param provider 어느 provider 로 로그인하는지
 * @param credential 앱이 provider SDK 에서 받아 넘긴 토큰. Apple·Google 은 ID 토큰(JWT), Kakao 는 액세스 토큰
 * @param nickname 앱이 함께 넘긴 표시 이름. Apple 은 최초 인증 응답에만 이름을 주므로 그때 받아 넘기지 않으면 영영
 *     얻을 수 없다. 없을 수 있다
 * @param email 앱이 함께 넘긴 이메일. 위와 같은 이유로 Apple 최초 로그인에서만 온다. 없을 수 있다
 * @param authorizationCode Apple 만 보낸다(#287). 탈퇴 시 연결을 끊으려면 refresh 토큰이 필요한데, 그것을 얻을
 *     이 코드는 <b>1회용·5분</b>이라 로그인 그 순간에 교환해야 한다. 없을 수 있다 — 없으면 연결 해제만 못 한다
 */
@Builder
public record SocialLoginCommand(
        AuthProvider provider,
        String credential,
        String nickname,
        String email,
        String authorizationCode) {}
