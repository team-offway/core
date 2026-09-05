package com.offway.core.common.external.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 연동 설정 변경(#403).
 *
 * <p><b>둘 다 래퍼 타입이다.</b> Jackson 3 은 선택 필드가 primitive 면 그 필드가 없을 때 매핑을 깨뜨려,
 * 어드민이 한 칸만 보내도 "값 오류" 로 보고된다(#354 에서 겪었다).
 *
 * @param cacheEnabled 인메모리 캐시를 쓸지. 생략하면 켠 것으로 본다
 * @param batchLimit 배치가 하루에 쓸 수 있는 상한. 생략·null 이면 무제한
 */
public record ExternalApiSettingRequest(
        @Schema(description = "인메모리 캐시 사용 여부. 끄면 매번 실호출한다", example = "true", nullable = true)
                Boolean cacheEnabled,
        @Schema(description = "배치 하루 상한. 비우면 무제한", example = "700", nullable = true)
                Integer batchLimit) {

    /** 생략은 "건드리지 않음" 이 아니라 <b>기본값</b>이다 — PATCH 지만 이 리소스는 필드가 둘뿐이라 통째로 받는다. */
    public boolean cacheEnabledOrDefault() {
        return cacheEnabled == null || cacheEnabled;
    }
}
