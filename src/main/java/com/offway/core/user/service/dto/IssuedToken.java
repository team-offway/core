package com.offway.core.user.service.dto;

/**
 * 발급된 토큰 쌍(내부용).
 *
 * @param accessToken 요청 인증용 JWT
 * @param refreshToken 재발급용 원문. 서버에는 해시만 남는다
 * @param expiresInSeconds access 토큰 잔여 수명(초)
 * @param newUser 이 발급이 가입이었는지. 재발급·개발 로그인에서는 의미가 없어 {@code false}
 */
public record IssuedToken(String accessToken, String refreshToken, long expiresInSeconds, boolean newUser) {}
