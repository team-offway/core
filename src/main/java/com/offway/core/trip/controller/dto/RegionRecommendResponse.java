package com.offway.core.trip.controller.dto;

import com.offway.core.common.logging.LogSummaries;
import com.offway.core.common.logging.LogSummary;
import com.offway.core.trip.domain.CrowdLevel;
import com.offway.core.trip.service.dto.RecommendedRegion;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 여행지 추천 응답 — API 계약. 랭킹 순(무드 지정 시 매칭 지역 우선).
 *
 * @param regions 추천 지역 (랭킹 내림차순)
 */
public record RegionRecommendResponse(List<Item> regions) implements LogSummary {

    public static RegionRecommendResponse from(List<RecommendedRegion> regions) {
        return new RegionRecommendResponse(regions.stream().map(Item::from).toList());
    }

    @Override
    public String logSummary() {
        return LogSummaries.count("추천", regions);
    }

    /**
     * @param regionId 지역 ID
     * @param name 지역명 (시군구 · 시도)
     * @param lat 대표 좌표 위도(#404)
     * @param lng 대표 좌표 경도
     * @param reachMinutes 출발지→지역 도달시간(분)
     * @param crowdLevel 한산도 뱃지
     * @param imageUrl 대표 이미지 URL (없으면 null)
     * @param contentCount 볼거리 수 (인접 50km 병합 시 합산)
     * @param categories 볼거리 카테고리 태그 (필터칩과 달리 개수가 없다 — {@link CategoryTagResponse})
     * @param neighborIncluded 볼거리 부족으로 인접 50km 지역이 포함됐는지
     * @param benefits 적용 혜택 뱃지
     */
    public record Item(
            long regionId,
            @Schema(example = "완도군 · 전남광주통합특별시") String name,
            @Schema(description = """
                    대표 좌표(WGS84) — 지도 위에 이 지역 칩을 놓는 자리(#404).

                    군청·시청 소재지 기준으로 통일돼 있다. **항상 실린다** — 89곳 전부 좌표를 갖고
                    있고(`NOT NULL`), 애초에 좌표가 없으면 도달시간을 못 재 추천에 오르지도 못한다.""",
                    example = "37.381") double lat,
            @Schema(example = "128.661") double lng,
            @Schema(example = "160") int reachMinutes,
            CrowdLevel crowdLevel,
            @Schema(
                            example = "http://tong.visitkorea.or.kr/cms/resource/83/1234583_image2_1.jpg",
                            nullable = true)
                    String imageUrl,
            @Schema(example = "38") int contentCount,
            List<CategoryTagResponse> categories,
            @Schema(example = "false") boolean neighborIncluded,
            @Schema(description = """
                    지역 한 줄 소개(#140). 그 지역에 실제로 있는 대표 볼거리 이름으로 만든다.

                    감성 카피가 아니라 사실이다 — 지역 소개를 주는 외부 출처가 없어, 지어내는 대신
                    우리가 가진 것(국가유산·볼거리)의 이름을 조합한다. 재료가 없으면 필드가 없다.""",
                    example = "탑리리 오층석탑과 고운사 가운루가 있는 곳", nullable = true) String intro,
            List<Benefit> benefits) {

        static Item from(RecommendedRegion region) {
            return new Item(
                    region.regionId(),
                    region.sigungu() + " · " + region.sido(),
                    // 값객체에서 꺼내 두 칸으로 편다 — 앱이 이미 lat·lng 를 평평하게 읽는다.
                    region.coordinate().lat(),
                    region.coordinate().lng(),
                    region.reachMinutes(),
                    region.crowdLevel(),
                    region.imageUrl(),
                    region.contentCount(),
                    region.categories().stream().map(CategoryTagResponse::from).toList(),
                    region.neighborIncluded(),
                    region.intro(),
                    region.benefits().stream().map(Benefit::from).toList());
        }
    }

    /**
     * @param policyId 정책 ID
     * @param text 뱃지 문구
     */
    public record Benefit(long policyId, String text) {

        static Benefit from(RecommendedRegion.Benefit benefit) {
            return new Benefit(benefit.policyId(), benefit.text());
        }
    }
}
