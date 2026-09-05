package com.offway.core.trip.controller.dto;

import com.offway.core.common.logging.LogSummaries;
import com.offway.core.common.logging.LogSummary;
import com.offway.core.common.response.Attributed;
import com.offway.core.common.response.DataSource;
import com.offway.core.trip.domain.CrowdLevel;
import com.offway.core.trip.service.dto.RegionList;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;
import lombok.Builder;

/**
 * 지역 목록 응답 — API 계약. 방문자 랭킹 내림차순.
 *
 * <p>페이지 메타({@code page}·{@code size}·{@code totalElements}·{@code totalPages})는 여기가 아니라 <b>공통 래퍼의
 * {@code pageResponse}</b> 로 나간다(api-convention). 목록 API 가 전부 같은 자리에서 페이지 정보를 주게 하려는 것이다.
 *
 * @param regions 이 페이지의 지역
 */
public record RegionListResponse(List<Item> regions) implements LogSummary, Attributed {

    public static RegionListResponse from(RegionList regions) {
        return new RegionListResponse(regions.regions().stream().map(Item::from).toList());
    }

    /**
     * 지역 카드의 사진·볼거리 분류·한산도는 전부 공사에서 온다(#399) — 관광 API 와 관광빅데이터다.
     *
     * <p>목록 이 비면 표기할 것도 없다. 있는 것만 세는 규칙을 여기서도 지킨다.
     */
    @Override
    public Set<DataSource> sources() {
        return regions.isEmpty() ? Set.of() : Set.of(DataSource.KTO);
    }

    @Override
    public String logSummary() {
        return LogSummaries.count("지역", regions);
    }

    /**
     * @param regionId 지역 ID
     * @param name 지역명 (시군구 · 시도)
     * @param lat 대표 좌표 위도(#404)
     * @param lng 대표 좌표 경도
     * @param crowdLevel 한산도 뱃지
     * @param imageUrl 대표 이미지 URL (없으면 null)
     * @param contentCount 볼거리 수 (인접 50km 병합 시 합산)
     * @param categories 볼거리 카테고리 태그
     * @param neighborIncluded 볼거리 부족으로 인접 50km 지역이 포함됐는지
     */
    @Builder
    public record Item(
            long regionId,
            @Schema(example = "완도군 · 전남광주통합특별시") String name,
            @Schema(description = """
                    대표 좌표(WGS84) — 지도 위에 이 지역 칩을 놓는 자리(#404).

                    추천 응답과 같은 값·같은 이름이다. 목록은 추천의 "더보기" 라 카드 재료가 갈리면
                    앱이 화면마다 다른 파서를 들게 된다.""",
                    example = "37.381") double lat,
            @Schema(example = "128.661") double lng,
            CrowdLevel crowdLevel,
            @Schema(
                            example = "http://tong.visitkorea.or.kr/cms/resource/83/1234583_image2_1.jpg",
                            nullable = true)
                    String imageUrl,
            @Schema(example = "38") int contentCount,
            List<CategoryTagResponse> categories,
            @Schema(example = "false") boolean neighborIncluded) {

        static Item from(RegionList.Item region) {
            // 이름을 붙여 조립한다 — lat·lng 가 나란한 double 이라 맞바꿔도 컴파일이 통과한다.
            return Item.builder()
                    .regionId(region.regionId())
                    .name(region.sigungu() + " · " + region.sido())
                    .lat(region.coordinate().lat())
                    .lng(region.coordinate().lng())
                    .crowdLevel(region.crowdLevel())
                    .imageUrl(region.imageUrl())
                    .contentCount(region.contentCount())
                    .categories(region.categories().stream().map(CategoryTagResponse::from).toList())
                    .neighborIncluded(region.neighborIncluded())
                    .build();
        }
    }
}
