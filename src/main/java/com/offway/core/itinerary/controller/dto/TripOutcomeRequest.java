package com.offway.core.itinerary.controller.dto;

import com.offway.core.itinerary.domain.TripFeedback;
import com.offway.core.itinerary.domain.VisitOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

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
                        description = """
                                남기고 싶은 말 (선택). **200자**까지.

                                이어진 공백은 한 칸으로 접히고(줄바꿈·탭 포함), 앞뒤 공백을 걷어낸 뒤 비면
                                없는 것으로 본다. 길이는 **접은 뒤**에 잰다.

                                상한을 넘으면 400(ITINERARY-011)이다.""",
                        example = "버스 배차가 아쉬웠어요",
                        nullable = true)
                String comment) {

    /**
     * 평가를 값객체로 옮긴다 — 공백 접기와 범위 검증은 그쪽이 소유한다.
     *
     * <h2>별점은 양쪽에서 보고, 한 줄은 도메인만 본다</h2>
     *
     * 별점에는 정규화가 없어 {@code @Min}·{@code @Max} 와 값객체의 판단이 <b>같다</b> — 그래서 양쪽에
     * 둔다(exception-and-response: 검증은 입력 경계와 도메인 양쪽에).
     *
     * <p>한 줄은 다르다. {@code @Size} 는 <b>원문</b> 길이를 보고 값객체는 <b>공백을 접은 뒤</b> 길이를
     * 보므로, 앞뒤 공백 4자 + 본문 200자 같은 값이 경계에서는 막히고 도메인에서는 통과한다. 같은 규칙을
     * 두 곳에 다르게 적는 것은 defense in depth 가 아니라 <b>모순</b>이라, 도메인 하나로 모았다.
     *
     * <p>그래서 응답 코드가 갈린다 — 별점 범위는 {@code COMMON-400}(Bean Validation), 한 줄 길이는
     * {@code ITINERARY-011}(도메인). {@code *Api} 에 그대로 적었다.
     */
    public TripFeedback toFeedback() {
        return TripFeedback.of(rating, comment);
    }
}
