package com.offway.core.itinerary.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 여행지 평가 값객체 — 범위·공백 접기(#592). */
class TripFeedbackTest {

    @Test
    void 아무것도_안_남기면_none_이다() {
        // 건너뛰기가 정상 상태다 — 모달의 본업은 연차 차감이다.
        assertSame(TripFeedback.none(), TripFeedback.of(null, null));
        assertFalse(TripFeedback.none().isPresent());
    }

    @Test
    void 별점만_남길_수_있다() {
        TripFeedback feedback = TripFeedback.of(4, null);

        assertTrue(feedback.isPresent());
        assertEquals(4, feedback.rating());
        assertTrue(feedback.commentValue().isEmpty());
    }

    @Test
    void 한_줄만_남길_수_있다() {
        TripFeedback feedback = TripFeedback.of(null, "버스 배차가 아쉬웠어요");

        assertTrue(feedback.isPresent());
        assertTrue(feedback.ratingValue().isEmpty());
        assertEquals("버스 배차가 아쉬웠어요", feedback.comment());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n  \n"})
    void 공백뿐인_한_줄은_없는_것으로_접힌다(String blank) {
        // 입력창을 눌렀다 지우면 빈 문자열이 온다. 그대로 저장하면 "코멘트가 있는 행" 으로 세어져
        // 지역별 집계가 실제보다 많은 의견이 있는 것처럼 보인다.
        assertSame(TripFeedback.none(), TripFeedback.of(null, blank));
    }

    @Test
    void 별점이_있으면_공백_한_줄만_접고_평가는_남는다() {
        TripFeedback feedback = TripFeedback.of(5, "   ");

        assertTrue(feedback.isPresent());
        assertEquals(5, feedback.rating());
        assertTrue(feedback.commentValue().isEmpty());
    }

    @Test
    void 앞뒤_공백을_걷어낸다() {
        assertEquals("좋았어요", TripFeedback.of(null, "  좋았어요  ").comment());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "좋았어요\n교통은 아쉬웠어요",
        "좋았어요\r\n교통은 아쉬웠어요",
        "좋았어요\t교통은 아쉬웠어요",
        "좋았어요    교통은 아쉬웠어요",
    })
    void 안쪽_공백은_한_칸으로_접는다(String raw) {
        // trim() 은 앞뒤만 걷어내므로 줄바꿈이 섞인 값이 그대로 통과했다 — 한 줄 계약과 어긋난다.
        // **거절하지 않고 접는 것은 판단이다.** 선택 항목이라 거절하면 그 의견을 통째로 잃는다.
        assertEquals("좋았어요 교통은 아쉬웠어요", TripFeedback.of(null, raw).comment());
    }

    @Test
    void 줄바꿈뿐인_한_줄도_없는_것으로_접힌다() {
        assertSame(TripFeedback.none(), TripFeedback.of(null, "\n\r\n  \t"));
    }

    @Test
    void 길이는_접은_뒤에_잰다() {
        // 안쪽 공백이 접히므로 원문이 상한을 넘어도 접은 뒤 상한 안이면 통과한다.
        // 요청 DTO 에 @Size 를 두지 않은 이유가 이것이다 — Bean Validation 은 원문을 본다.
        String raw = "가".repeat(100) + "          " + "가".repeat(99);
        assertEquals(200, TripFeedback.of(null, raw).comment().length());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void 별점은_1부터_5까지다(int rating) {
        assertEquals(rating, TripFeedback.of(rating, null).rating());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 6, 100})
    void 범위를_벗어난_별점은_거절한다(int rating) {
        assertThrows(ItineraryException.class, () -> TripFeedback.of(rating, null));
    }

    @Test
    void 상한을_넘는_한_줄은_거절한다() {
        String tooLong = "가".repeat(TripFeedback.MAX_COMMENT_LENGTH + 1);

        assertThrows(ItineraryException.class, () -> TripFeedback.of(null, tooLong));
    }

    @Test
    void 상한과_같은_길이는_통과한다() {
        String atLimit = "가".repeat(TripFeedback.MAX_COMMENT_LENGTH);

        assertEquals(atLimit, TripFeedback.of(null, atLimit).comment());
    }

    @Test
    void 앞뒤_공백을_걷어낸_뒤_길이를_잰다() {
        // 공백까지 세어 거절하면 사용자는 왜 막혔는지 모른다.
        String padded = "  " + "가".repeat(TripFeedback.MAX_COMMENT_LENGTH) + "  ";

        assertEquals(TripFeedback.MAX_COMMENT_LENGTH, TripFeedback.of(null, padded).comment().length());
    }
}
