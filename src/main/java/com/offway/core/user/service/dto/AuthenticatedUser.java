package com.offway.core.user.service.dto;

import java.util.UUID;

/**
 * 로그인이 확정한 사용자(내부용).
 *
 * <p>{@code newUser} 는 <b>이 로그인이 가입이었는지</b>다. 앱은 이 값으로 온보딩(잔여 연차 입력)과 홈을 가른다.
 * "가입 시각이 방금인가" 같은 시간 비교로 나중에 되묻지 않는다 — 그 방식은 경계값에서 흔들리고, 재로그인이 느린 날
 * 기존 사용자를 온보딩으로 보낸다. 판정은 신원을 새로 만든 그 자리에서만 할 수 있다.
 *
 * @param userId 우리 서비스의 사용자 식별자
 * @param newUser 이 요청으로 새 사용자가 만들어졌으면 {@code true}
 */
public record AuthenticatedUser(UUID userId, boolean newUser) {}
