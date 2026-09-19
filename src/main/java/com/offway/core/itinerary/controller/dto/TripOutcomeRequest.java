package com.offway.core.itinerary.controller.dto;

import com.offway.core.itinerary.domain.TripFeedback;
import com.offway.core.itinerary.domain.VisitOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 홈 모달 "다녀오셨나요?" 의 답(#116) — 여행지 평가까지 함께 받는다(#592).
 *
 * <p><b>차감 일수를 받지 않는다.</b> 서버가 저장된 여행 날짜로 평일−공휴일을 다시 계산한다 — 클라이언트가 보낸
 * 값을 믿으면 임의 차감이 된다.
 *
 * @param outcome 다녀왔으면 {@code VISITED}(연차 차감), 안 갔으면 {@code NOT_VISITED}(차감 없음)
 * @param rating 여행지 별점 1~5. <b>선택</b> — 건너뛰면 없다
 * @param comment 남기고 싶은 말. <b>선택</b> — 공백뿐이면 없는 것으로 접힌다
 */
@Schema(description = "지난 여행 결과")
public record TripOutcomeRequest(
        @Schema(description = "VISITED = 다녀옴(연차 차감) · NOT_VISITED = 안 감(차감 없음)",
                        example = "VISITED",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull VisitOutcome outcome,
        @Schema(
                        description = """
                                이번 여행지 별점 1~5 (선택).

                                건너뛰기를 누르면 보내지 않는다 — 모달의 본업은 연차 차감이라 평가가
                                그것을 막지 않는다.

                                **안 갔다고 답할 때는 보낼 수 없다**(`NOT_VISITED` + 평가 → 400).
                                안 간 여행지는 평가가 성립하지 않는다.""",
                        example = "4",
                        nullable = true)
                @Min(TripFeedback.MIN_RATING) @Max(TripFeedback.MAX_RATING) Integer rating,
        @Schema(
                        description = "남기고 싶은 말 (선택). 앞뒤 공백을 걷어내고, 공백뿐이면 없는 것으로 본다.",
                        example = "버스 배차가 아쉬웠어요",
                        nullable = true)
                @Size(max = TripFeedback.MAX_COMMENT_LENGTH) String comment) {

    /**
     * 평가를 값객체로 옮긴다 — 공백 접기와 범위 검증은 그쪽이 소유한다.
     *
     * <p>Bean Validation 이 이미 범위를 보지만 값객체가 다시 본다. <b>검증은 입력 경계와 도메인 양쪽에
     * 둔다</b>(exception-and-response) — 도메인은 누가 만들든 스스로 유효함을 보장하는 최후의 보루다.
     */
    public TripFeedback toFeedback() {
        return TripFeedback.of(rating, comment);
    }
}
