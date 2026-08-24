package com.offway.core.user.service.dto;

import com.offway.core.user.domain.AuthProvider;

/**
 * 소셜 로그인 커맨드(내부용).
 *
 * @param provider 어느 provider 로 로그인하는지
 * @param credential 앱이 provider SDK 에서 받아 넘긴 토큰. Apple·Google 은 ID 토큰(JWT), Kakao 는 액세스 토큰
 * @param nickname 앱이 함께 넘긴 표시 이름. Apple 은 최초 인증 응답에만 이름을 주므로 그때 받아 넘기지 않으면 영영
 *     얻을 수 없다. 없을 수 있다
 * @param email 앱이 함께 넘긴 이메일. 위와 같은 이유로 Apple 최초 로그인에서만 온다. 없을 수 있다
 */
public record SocialLoginCommand(AuthProvider provider, String credential, String nickname, String email) {}
