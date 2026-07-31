package com.offway.core.itinerary.controller.dto;

import com.offway.core.itinerary.domain.VisitOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 홈 모달 "다녀오셨나요?" 의 답(#116).
 *
 * <p><b>차감 일수를 받지 않는다.</b> 서버가 저장된 여행 날짜로 평일−공휴일을 다시 계산한다 — 클라이언트가 보낸
 * 값을 믿으면 임의 차감이 된다.
 *
 * @param outcome 다녀왔으면 {@code VISITED}(연차 차감), 안 갔으면 {@code NOT_VISITED}(차감 없음)
 */
@Schema(description = "지난 여행 결과")
public record TripOutcomeRequest(
        @Schema(description = "VISITED = 다녀옴(연차 차감) · NOT_VISITED = 안 감(차감 없음)",
                        example = "VISITED",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull VisitOutcome outcome) {
}
