package com.offway.core.trip.service.dto;

import com.offway.core.common.geo.Coordinate;
import com.offway.core.trip.domain.Category;
import com.offway.core.trip.domain.CrowdLevel;
import com.offway.core.trip.domain.RegionContent;
import lombok.Builder;
import java.util.Comparator;
import java.util.List;

/**
 * 추천 결과 한 건(랭킹 순).
 *
 * @param regionId 지역 식별자
 * @param sido 시도
 * @param sigungu 시군구
 * @param coordinate 대표 좌표 — 지도 위에 이 지역을 놓는 자리(#404)
 * @param reachMinutes 출발지→지역 도달시간(분)
 * @param crowdLevel 한산도 뱃지(표시용)
 * @param contentCount 볼거리 수(인접 50km 병합 시 합산)
 * @param imageUrl 대표 이미지 URL(없으면 null)
 * @param categories 이 지역에 존재하는 카테고리(무드칩과 동일 분류)
 * @param neighborIncluded 볼거리 부족으로 인접 50km 지역이 병합됐는지
 * @param benefits 이 지역에 적용되는 혜택 뱃지
 */
@Builder
public record RecommendedRegion(
        long regionId,
        String sido,
        String sigungu,
        Coordinate coordinate,
        int reachMinutes,
        CrowdLevel crowdLevel,
        int contentCount,
        String imageUrl,
        List<Category> categories,
        boolean neighborIncluded,
        String intro,
        List<Benefit> benefits) {

    /**
     * 랭킹·도달·혜택에 지역 콘텐츠를 얹어 결과 한 건을 만든다.
     *
     * <p><b>대표 사진은 사다리로 고른다</b>(#196) — 중심 관광지 × 관광사진 갤러리가 먼저고, 못 고르면
     * TourAPI 표본의 이미지로 내려간다.
     *
     * @param heroPhotoUrl 갤러리에서 고른 대표 사진. 못 골랐으면 null
     */
    public static RecommendedRegion of(
            long regionId,
            String sido,
            String sigungu,
            Coordinate coordinate,
            int reachMinutes,
            CrowdLevel crowdLevel,
            RegionContent content,
            String heroPhotoUrl,
            String intro,
            List<Benefit> benefits) {
        // 이름을 붙여 조립한다 — sido·sigungu 처럼 같은 타입이 붙어 있어 위치 생성자로는 둘을
        // 맞바꿔도 컴파일이 통과한다. 그러면 "강원특별자치도 · 정선군" 이 뒤집힌 채 화면까지 간다.
        return RecommendedRegion.builder()
                .regionId(regionId)
                .sido(sido)
                .sigungu(sigungu)
                .coordinate(coordinate)
                .reachMinutes(reachMinutes)
                .crowdLevel(crowdLevel)
                .contentCount(content.contentCount())
                .imageUrl(heroPhotoUrl != null ? heroPhotoUrl : content.imageUrl())
                .categories(content.categories())
                .neighborIncluded(content.neighborIncluded())
                .intro(intro)
                .benefits(benefits)
                .build();
    }

    /** 무드칩에 해당하는 콘텐츠가 이 지역에 있는가(무드 필터용). */
    public boolean matchesMood(Category mood) {
        return categories.contains(mood);
    }

    /**
     * 무드칩 매칭 지역을 앞세운 재정렬(F6). 안정 정렬이라 그룹 내부는 원래(랭킹) 순서를 지킨다. 필터가 없거나({@code null}·
     * {@code ALL}) 매칭이 하나도 없으면 원본 순서 그대로 — 무드 때문에 결과가 비지 않게 한다.
     */
    public static List<RecommendedRegion> orderByMood(List<RecommendedRegion> regions, Category mood) {
        if (mood == null || mood == Category.ALL) {
            return regions;
        }
        boolean anyMatch = regions.stream().anyMatch(region -> region.matchesMood(mood));
        if (!anyMatch) {
            return regions;
        }
        return regions.stream()
                .sorted(Comparator.comparing((RecommendedRegion region) -> region.matchesMood(mood)).reversed())
                .toList();
    }

    /**
     * @param policyId 정책 ID
     * @param text 뱃지 문구
     */
    public record Benefit(long policyId, String text) {
    }
}
