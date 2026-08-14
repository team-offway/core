package com.offway.core.trip.domain;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * 필터칩별 지역 수 — "이 칩으로 좁히면 몇 곳이 나오는가"(#266).
 *
 * <p><b>왜 필요한가.</b> 칩 목록은 라벨만 주고 개수를 주지 않아, 앱이 개수를 전부 1 로 채워 "있다/없다" 만 판별하고 있었다. 칩에 개수를
 * 보여주거나 빈 칩을 가리려면 실제 수가 있어야 한다.
 *
 * <p><b>세는 규칙은 하나다.</b> {@link RegionContent#has} 를 그대로 쓴다 — 목록 필터가 쓰는 판정과 같은 것이어야 개수와 결과가
 * 어긋나지 않는다.
 *
 * @param byCategory 칩 → 그 칩으로 좁혔을 때 나오는 지역 수
 */
public record CategoryCounts(Map<Category, Integer> byCategory) {

    /** 아직 세지 않은 상태 — 모든 칩이 0 이다. */
    public static final CategoryCounts EMPTY = new CategoryCounts(Map.of());

    public CategoryCounts {
        byCategory = Map.copyOf(Objects.requireNonNull(byCategory, "칩별 지역 수는 필수입니다"));
    }

    /**
     * 지역별 콘텐츠에서 칩 개수를 센다. 입력에서 도출되는 값이라 빌더가 아니라 팩토리다(조립이면 빌더, 계산이면 팩토리).
     *
     * @param regionContents 지역 <b>전부</b>의 콘텐츠. 아직 콘텐츠가 없는 지역도 {@link RegionContent#EMPTY} 로 들어와야
     *     {@code ALL} 이 전체 지역 수가 된다 — 빠뜨리면 "전체" 칩이 목록보다 작아진다
     */
    public static CategoryCounts of(Collection<RegionContent> regionContents) {
        Objects.requireNonNull(regionContents, "지역 콘텐츠 목록은 필수입니다");
        Map<Category, Integer> counts = new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            counts.put(category, (int) regionContents.stream().filter(content -> content.has(category)).count());
        }
        return new CategoryCounts(counts);
    }

    /** 이 칩으로 좁혔을 때 나오는 지역 수. 세지 않았으면 0. */
    public int of(Category category) {
        return byCategory.getOrDefault(category, 0);
    }
}
