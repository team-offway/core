package com.offway.core.trip.controller.dto;

import com.offway.core.common.logging.LogSummaries;
import com.offway.core.common.logging.LogSummary;
import com.offway.core.trip.domain.CrowdLevel;
import com.offway.core.trip.service.dto.RegionList;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 지역 목록 응답 — API 계약. 방문자 랭킹 내림차순.
 *
 * <p>페이지 메타({@code page}·{@code size}·{@code totalElements}·{@code totalPages})는 여기가 아니라 <b>공통 래퍼의
 * {@code pageResponse}</b> 로 나간다(api-convention). 목록 API 가 전부 같은 자리에서 페이지 정보를 주게 하려는 것이다.
 *
 * @param regions 이 페이지의 지역
 */
public record RegionListResponse(List<Item> regions) implements LogSummary {

    public static RegionListResponse from(RegionList regions) {
        return new RegionListResponse(regions.regions().stream().map(Item::from).toList());
    }

    @Override
    public String logSummary() {
        return LogSummaries.count("지역", regions);
    }

    /**
     * @param regionId 지역 ID
     * @param name 지역명 (시군구 · 시도)
     * @param crowdLevel 한산도 뱃지
     * @param imageUrl 대표 이미지 URL (없으면 null)
     * @param contentCount 볼거리 수 (인접 50km 병합 시 합산)
     * @param categories 볼거리 카테고리 태그
     * @param neighborIncluded 볼거리 부족으로 인접 50km 지역이 포함됐는지
     */
    public record Item(
            long regionId,
            @Schema(example = "완도군 · 전남광주통합특별시") String name,
            CrowdLevel crowdLevel,
            @Schema(
                            example = "http://tong.visitkorea.or.kr/cms/resource/83/1234583_image2_1.jpg",
                            nullable = true)
                    String imageUrl,
            @Schema(example = "38") int contentCount,
            List<CategoryTagResponse> categories,
            @Schema(example = "false") boolean neighborIncluded) {

        static Item from(RegionList.Item region) {
            return new Item(
                    region.regionId(),
                    region.sigungu() + " · " + region.sido(),
                    region.crowdLevel(),
                    region.imageUrl(),
                    region.contentCount(),
                    region.categories().stream().map(CategoryTagResponse::from).toList(),
                    region.neighborIncluded());
        }
    }
}
