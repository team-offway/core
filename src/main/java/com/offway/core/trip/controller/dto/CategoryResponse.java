package com.offway.core.trip.controller.dto;

import com.offway.core.trip.domain.Category;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.List;

/**
 * 필터칩 카테고리 목록 응답 — API 계약.
 *
 * @param categories 노출 순서대로의 카테고리 칩
 */
public record CategoryResponse(List<Item> categories) {

    /** 도메인 {@link Category} 전부를 선언 순서대로 노출한다(ALL 이 맨 앞). */
    public static CategoryResponse of() {
        return new CategoryResponse(Arrays.stream(Category.values()).map(Item::from).toList());
    }

    /**
     * @param key enum 식별자 (ALL·SIGHT·STAY·EXPERIENCE·FOOD)
     * @param label 한글 라벨
     */
    public record Item(
            @Schema(example = "SIGHT") String key,
            @Schema(example = "관광지") String label) {

        static Item from(Category category) {
            return new Item(category.name(), category.label());
        }
    }
}
