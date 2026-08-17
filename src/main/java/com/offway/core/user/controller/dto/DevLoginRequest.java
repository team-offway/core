package com.offway.core.user.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 개발용 로그인 요청(local 전용).
 *
 * @param nickname 표시 이름(선택). 없으면 기본 이름이 붙는다
 */
public record DevLoginRequest(
        @Schema(description = "표시 이름(선택)", example = "테스터", nullable = true) String nickname) {}
