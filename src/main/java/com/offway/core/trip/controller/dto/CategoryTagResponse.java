package com.offway.core.trip.controller.dto;

import com.offway.core.trip.domain.Category;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 지역 카드에 붙는 볼거리 분류 태그 — "이 지역에 이런 것이 있다".
 *
 * <p><b>필터칩({@link CategoryResponse.Item})과 다른 타입이다.</b> 둘 다 {@code key}·{@code label} 을 갖지만 답하는 질문이 다르다 —
 * 필터칩은 "이 칩으로 좁히면 몇 곳인가"({@code regionCount})까지 답하고, 태그는 그 지역 카드의 표시일 뿐이라 개수라는 개념이 없다.
 * 한 타입으로 묶으면 지역 카드마다 전체 지역 수가 따라붙어 읽는 쪽이 그것을 그 지역의 수로 오해한다.
 *
 * @param key enum 식별자 (SIGHT·STAY·EXPERIENCE·FOOD)
 * @param label 한글 라벨
 */
public record CategoryTagResponse(
        @Schema(example = "SIGHT") String key,
        @Schema(example = "관광지") String label) {

    public static CategoryTagResponse from(Category category) {
        return new CategoryTagResponse(category.name(), category.label());
    }
}
