package com.offway.core.user.service.dto;

import java.util.UUID;

/**
 * refresh 회전 시도의 결과(내부용).
 *
 * <p>영속 계층이 예외를 던지지 않고 결과로 돌려주는 이유가 있다. 재사용(탈취) 감지 시 해야 할 "사용자 토큰 전체 폐기"를
 * 같은 트랜잭션 안에서 하고 예외를 던지면, 그 폐기까지 함께 롤백돼 탈취된 토큰이 그대로 살아남는다. 결과를 받은 조율 계층이
 * 폐기를 별도 트랜잭션으로 끝낸 뒤 예외를 던진다.
 */
public sealed interface TokenRotation {

    /** 정상 회전 — 기존 토큰은 폐기됐고 새 토큰이 저장됐다. */
    record Rotated(UUID userId) implements TokenRotation {}

    /** 이미 폐기된 토큰이 다시 왔다 — 탈취 정황이라 이 사용자의 토큰을 전부 끊어야 한다. */
    record Reused(UUID userId) implements TokenRotation {}

    /** 없는 토큰이거나 만료됨. 끊을 대상이 없다. */
    record Invalid() implements TokenRotation {}
}
