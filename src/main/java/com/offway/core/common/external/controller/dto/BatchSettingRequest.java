package com.offway.core.common.external.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 배치를 멈추거나 다시 돌린다(#403).
 *
 * <p>{@code Boolean} 인 이유는 {@link ExternalApiSettingRequest} 와 같다 — Jackson 3 은 선택 필드가
 * primitive 면 생략됐을 때 매핑을 깨뜨린다.
 */
public record BatchSettingRequest(
        @Schema(description = "false 면 주기가 와도 돌지 않는다", example = "false", nullable = true)
                Boolean enabled) {

    public boolean enabledOrDefault() {
        return enabled == null || enabled;
    }
}
