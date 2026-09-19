package com.offway.core.itinerary.domain;

import java.util.Optional;

/**
 * 여행 후 모달에서 받은 여행지 평가 — 별점과 한 줄(#592).
 *
 * <p><b>쓰임은 지자체 전달이다.</b> 우리가 다루는 곳이 인구감소지역 89곳이라, "다녀온 사람들이 무엇을
 * 좋아하고 무엇이 아쉬웠나" 가 사업 확장 근거가 된다. 지금 우리에게는 그 데이터가 전혀 없다.
 *
 * <p><b>이 값은 사람에 묶이지 않고 쌓인다.</b> 요청에서 받아 {@link RegionFeedback} 으로 옮기는데,
 * 그 표에는 사용자·코스 참조가 없다 — 지자체에 전달되는 값이라 개인을 특정할 수 없어야 한다.
 * 그래서 이 값객체는 <b>전달 통로</b>이고, 영속 상태를 들고 있지 않다.
 *
 * <p><b>왜 값객체인가.</b> 별점과 한 줄이 한 덩어리로 들어오고, 둘 다 없을 수도 있다. 검증을 서비스에
 * 흩어 놓으면 "별점만 있고 코멘트가 공백인" 조합마다 판단이 갈린다 — 그 판단을 여기 하나로 모은다.
 *
 * <p><b>둘 다 선택이다.</b> 모달의 본업은 연차 차감이라, 평가가 그것을 막으면 확인만 하려던 사용자가
 * 갇힌다. 억지로 받은 답은 지자체 근거로도 약하다. 그래서 {@link #none()} 이 정상 상태다.
 *
 * @param rating 1~5. 없으면 {@code null}
 * @param comment 남기고 싶은 말. 없거나 공백뿐이면 {@code null} 로 접힌다
 */
public record TripFeedback(Integer rating, String comment) {

    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 5;

    /**
     * 한 줄 길이 상한.
     *
     * <p>상한이 없으면 <b>요청 하나의 크기를 사용자가 정한다.</b> 그리고 이 값은 지자체에 전달되는
     * 자리라 사람이 읽을 분량이어야 한다 — 긴 글은 모달의 한 줄 입력창이 받을 모양도 아니다.
     */
    public static final int MAX_COMMENT_LENGTH = 200;

    /** 평가를 남기지 않은 상태 — 건너뛰기를 누른 경우다. */
    private static final TripFeedback NONE = new TripFeedback(null, null);

    public TripFeedback {
        if (rating != null && (rating < MIN_RATING || rating > MAX_RATING)) {
            throw ItineraryException.invalidTripFeedback();
        }
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            throw ItineraryException.invalidTripFeedback();
        }
    }

    public static TripFeedback none() {
        return NONE;
    }

    /**
     * 요청 값에서 만든다 — 공백뿐인 한 줄은 없는 것으로 접는다.
     *
     * <p><b>왜 접나.</b> 사용자가 입력창을 눌렀다 지우면 빈 문자열이 온다. 그것을 그대로 저장하면
     * "코멘트가 있는 행" 으로 세어져, 지역별 집계가 실제보다 많은 의견이 있는 것처럼 보인다.
     *
     * <p>길이 검증은 <b>다듬은 뒤</b>에 한다 — 앞뒤 공백까지 세어 거절하면 사용자는 왜 막혔는지 모른다.
     */
    public static TripFeedback of(Integer rating, String comment) {
        String trimmed = comment == null ? null : comment.trim();
        if (trimmed != null && trimmed.isEmpty()) {
            trimmed = null;
        }
        if (rating == null && trimmed == null) {
            return NONE;
        }
        return new TripFeedback(rating, trimmed);
    }

    /** 무언가라도 남겼는가 — 저장·집계가 이 값을 본다. */
    public boolean isPresent() {
        return rating != null || comment != null;
    }

    /** 별점 — 없을 수 있다. */
    public Optional<Integer> ratingValue() {
        return Optional.ofNullable(rating);
    }

    /** 한 줄 — 없을 수 있다. */
    public Optional<String> commentValue() {
        return Optional.ofNullable(comment);
    }
}
