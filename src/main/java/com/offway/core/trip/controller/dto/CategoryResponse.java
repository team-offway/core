package com.offway.core.trip.controller.dto;

import com.offway.core.trip.domain.Category;
import com.offway.core.trip.domain.CategoryCounts;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.List;

/**
 * 필터칩 카테고리 목록 응답 — API 계약.
 *
 * <p>칩마다 <b>그 칩으로 좁혔을 때 나오는 지역 수</b>를 함께 낸다(#266). 없으면 화면이 개수를 지어내거나("전부 1건") 빈 칩을 그대로
 * 그린다.
 *
 * @param categories 노출 순서대로의 카테고리 칩
 */
public record CategoryResponse(List<Item> categories) {

    /** 도메인 {@link Category} 전부를 선언 순서대로 노출한다(ALL 이 맨 앞). */
    public static CategoryResponse of(CategoryCounts counts) {
        return new CategoryResponse(
                Arrays.stream(Category.values()).map(category -> Item.from(category, counts)).toList());
    }

    /**
     * @param key enum 식별자 (ALL·SIGHT·STAY·EXPERIENCE·FOOD)
     * @param label 한글 라벨
     * @param regionCount 이 칩으로 좁혔을 때 나오는 인구감소지역 수. {@code ALL} 은 전체 지역 수다
     */
    public record Item(
            @Schema(example = "SIGHT") String key,
            @Schema(example = "관광지") String label,
            @Schema(description = "이 칩으로 좁혔을 때 나오는 지역 수 (ALL 은 전체)", example = "61") int regionCount) {

        static Item from(Category category, CategoryCounts counts) {
            return new Item(category.name(), category.label(), counts.of(category));
        }
    }
}
