package com.offway.core.trip.controller.dto;

import com.offway.core.common.logging.LogSummaries;
import com.offway.core.common.logging.LogSummary;
import com.offway.core.common.response.Attributed;
import com.offway.core.common.response.DataSource;
import com.offway.core.trip.domain.CrowdLevel;
import com.offway.core.trip.service.dto.RecommendedRegion;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;
import lombok.Builder;

/**
 * 여행지 추천 응답 — API 계약. 랭킹 순(무드 지정 시 매칭 지역 우선).
 *
 * @param regions 추천 지역 (랭킹 내림차순)
 */
public record RegionRecommendResponse(List<Item> regions) implements LogSummary, Attributed {

    public static RegionRecommendResponse from(List<RecommendedRegion> regions) {
        return new RegionRecommendResponse(regions.stream().map(Item::from).toList());
    }

    /**
     * 지역 카드의 사진·볼거리 분류·한산도는 전부 공사에서 온다(#399) — 관광 API 와 관광빅데이터다.
     *
     * <p>추천 결과 이 비면 표기할 것도 없다. 있는 것만 세는 규칙을 여기서도 지킨다.
     */
    @Override
    public Set<DataSource> sources() {
        return regions.isEmpty() ? Set.of() : Set.of(DataSource.KTO);
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
    @Builder
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
            // 이름을 붙여 조립한다. lat·lng 가 나란한 double 이라 위치 생성자로는 둘을 맞바꿔도
            // 컴파일이 통과하고, 뒤집힌 좌표는 지도에 핀이 엉뚱한 데 뜰 때까지 아무도 모른다.
            return Item.builder()
                    .regionId(region.regionId())
                    .name(region.sigungu() + " · " + region.sido())
                    // 값객체에서 꺼내 두 칸으로 편다 — 앱이 이미 lat·lng 를 평평하게 읽는다.
                    .lat(region.coordinate().lat())
                    .lng(region.coordinate().lng())
                    .reachMinutes(region.reachMinutes())
                    .crowdLevel(region.crowdLevel())
                    .imageUrl(region.imageUrl())
                    .contentCount(region.contentCount())
                    .categories(region.categories().stream().map(CategoryTagResponse::from).toList())
                    .neighborIncluded(region.neighborIncluded())
                    .intro(region.intro())
                    .benefits(region.benefits().stream().map(Benefit::from).toList())
                    .build();
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
