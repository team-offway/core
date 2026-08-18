package com.offway.core.user.service.dto;

import com.offway.core.user.domain.AuthProvider;
import java.time.Instant;

/**
 * 내 정보 조회 결과(#282) — 서비스가 컨트롤러에 넘기는 내부 dto.
 *
 * @param nickname 표시 이름. 가입 때 provider 값이 없었으면 기본값이 들어가 있다
 * @param email 이메일. <b>null 일 수 있다</b> — 카카오는 동의를 안 하면 안 주고, Apple 은 최초 로그인에만 준다
 * @param provider 어느 provider 로 로그인했는지. <b>null 일 수 있다</b> — local 개발 로그인은 연결이 없다
 * @param joinedAt 가입 시각(UTC 기준 절대시각). 표시 시간대는 응답 dto 가 정한다
 */
public record MyUser(String nickname, String email, AuthProvider provider, Instant joinedAt) {}
