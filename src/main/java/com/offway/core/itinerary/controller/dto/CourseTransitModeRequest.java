package com.offway.core.itinerary.controller.dto;

import com.offway.core.transport.domain.TransitMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 저장 코스의 대중교통 수단을 바꾼다(#456) — 상세 화면의 "기차로 보기" 칩.
 *
 * <p><b>고정을 푸는 값은 받지 않는다.</b> 화면에는 늘 어느 칩이 선택돼 있고 "서버에게 맡기기" 라는 칩이
 * 없어서, null 을 허용하면 계약에만 있고 아무도 안 쓰는 상태가 생긴다. 필요해지면 그때 연다.
 *
 * @param transitMode 이 코스로 갈 수단. 응답의 대안 목록(alternatives)에 있는 값 중 하나를 보낸다
 */
@Schema(description = "저장 코스의 대중교통 수단 변경 요청")
public record CourseTransitModeRequest(
        @Schema(example = "TRAIN", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull TransitMode transitMode) {
}
