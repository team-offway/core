package com.offway.core.user.service.dto;

import com.offway.core.user.domain.AuthProvider;

/**
 * 로그인 커맨드(내부용).
 *
 * @param provider 어느 provider 로 로그인하는지
 * @param idToken 앱이 provider SDK 에서 받아 온 ID 토큰
 * @param nickname 앱이 함께 넘긴 표시 이름. Apple 은 ID 토큰에 이름이 없어 이 값이 유일한 출처다. 없을 수 있다
 */
public record LoginCommand(AuthProvider provider, String idToken, String nickname) {}
