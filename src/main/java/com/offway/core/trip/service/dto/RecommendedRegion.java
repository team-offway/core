package com.offway.core.trip.service.dto;

import com.offway.core.common.geo.Coordinate;
import com.offway.core.trip.domain.Category;
import com.offway.core.trip.domain.CrowdLevel;
import com.offway.core.trip.domain.RegionContent;
import com.offway.core.trip.domain.RegionVisitMetrics;
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
 * @param visitMetrics 한산한 요일·인기 추세(#394). 카드의 "최근 인기 상승" 칩과 정렬이 이 값을 쓴다
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
        List<Benefit> benefits,
        RegionVisitMetrics visitMetrics) {

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
            List<Benefit> benefits,
            RegionVisitMetrics visitMetrics) {
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
                .visitMetrics(visitMetrics)
                .build();
    }

    /** 무드칩에 해당하는 콘텐츠가 이 지역에 있는가(무드 필터용). */
    public boolean matchesMood(Category mood) {
        return categories.contains(mood);
    }

    /** 혜택 뱃지가 붙는가 — 추천순이 이것을 가장 앞세운다. */
    public boolean hasBenefit() {
        return !benefits.isEmpty();
    }

    /** "최근 인기 상승" 칩이 붙는가. 아직 못 재는 지역이면 거짓이다. */
    public boolean isRising() {
        return visitMetrics != null && visitMetrics.isRising();
    }

    /**
     * 추천순 재정렬 — <b>무드 → 혜택 → 최근 인기 상승 → 랭킹</b>.
     *
     * <p>순서에 이유가 있다. 무드는 사용자가 <b>직접 고른</b> 조건이라 가장 강하다. 혜택은 그 지역에
     * 가면 실제로 받는 것이라 다음이고, 인기 상승은 우리가 관측으로 얹는 참고값이라 그다음이다.
     * 셋이 갈리지 않는 지역들은 원래(랭킹) 순서를 지킨다 — 안정 정렬이다.
     *
     * <p>무드는 매칭이 하나도 없으면 <b>정렬에서 빠진다</b>. 그러면 전부 같은 값이 되어 순서만
     * 흔들 뿐인데, 무드 때문에 결과가 비어 보이는 것을 막으려는 기존 판단이다(F6).
     */
    public static List<RecommendedRegion> orderForRecommendation(
            List<RecommendedRegion> regions, Category mood) {
        // Boolean 은 false < true 라, 참을 앞세우려면 역순으로 비교한다. Comparator 를 이어 붙인 뒤
        // reversed() 를 부르면 이어 붙인 전체가 뒤집히므로 키마다 역순을 준다.
        Comparator<RecommendedRegion> order =
                Comparator.comparing(RecommendedRegion::hasBenefit, Comparator.reverseOrder())
                        .thenComparing(RecommendedRegion::isRising, Comparator.reverseOrder());
        if (usesMood(regions, mood)) {
            order = Comparator.comparing(
                            (RecommendedRegion region) -> region.matchesMood(mood), Comparator.reverseOrder())
                    .thenComparing(order);
        }
        return regions.stream().sorted(order).toList();
    }

    private static boolean usesMood(List<RecommendedRegion> regions, Category mood) {
        if (mood == null || mood == Category.ALL) {
            return false;
        }
        return regions.stream().anyMatch(region -> region.matchesMood(mood));
    }

    /**
     * @param policyId 정책 ID
     * @param text 뱃지 문구
     */
    public record Benefit(long policyId, String text) {
    }
}
