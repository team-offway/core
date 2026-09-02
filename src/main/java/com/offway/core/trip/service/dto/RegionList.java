package com.offway.core.trip.service.dto;

import com.offway.core.common.response.PageResponse;
import com.offway.core.transport.domain.Coordinate;
import com.offway.core.trip.domain.Category;
import com.offway.core.trip.domain.CrowdLevel;
import com.offway.core.trip.domain.RegionContent;
import java.util.List;
import lombok.Builder;
import org.springframework.data.domain.Page;

/**
 * 지역 목록 조회 결과(#266) — 한 페이지의 지역과 페이지 정보.
 *
 * @param regions 이 페이지의 지역들 (방문자 랭킹 내림차순)
 * @param page 0부터 시작하는 페이지 번호
 * @param size 페이지 크기
 * @param totalElements 필터를 적용한 전체 건수
 * @param totalPages 전체 페이지 수
 */
public record RegionList(List<Item> regions, int page, int size, long totalElements, int totalPages)
        implements PageResponse.Paged {

    public RegionList {
        regions = List.copyOf(regions);
    }

    public static RegionList from(Page<Item> page) {
        return new RegionList(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /**
     * 목록에 실리는 지역 한 곳. 홈 카드와 같은 재료를 쓴다 — 같은 화면의 "더보기" 라 카드 모양이 달라질 이유가 없다.
     *
     * @param regionId 지역 ID
     * @param sido 시도
     * @param sigungu 시군구
     * @param coordinate 대표 좌표 — 지도 위에 이 지역을 놓는 자리(#404)
     * @param crowdLevel 한산도 뱃지
     * @param imageUrl 대표 이미지 URL (없으면 null)
     * @param contentCount 볼거리 수 (인접 50km 병합 시 합산)
     * @param categories 볼거리 카테고리
     * @param neighborIncluded 볼거리 부족으로 인접 50km 지역이 포함됐는지 — {@code contentCount} 가 무엇의 합인지 설명한다
     */
    @Builder
    public record Item(
            long regionId,
            String sido,
            String sigungu,
            Coordinate coordinate,
            CrowdLevel crowdLevel,
            String imageUrl,
            int contentCount,
            List<Category> categories,
            boolean neighborIncluded) {

        /**
         * 랭킹·콘텐츠·대표 사진을 한 항목으로 조립한다.
         *
         * <p>대표 사진은 갤러리에서 고른 것이 먼저고, 못 골랐으면 콘텐츠 표본의 이미지로 내려간다(#196) —
         * {@link HomeResult.RegionCard#of} 와 같은 사다리다.
         *
         * @param heroPhotoUrl 갤러리에서 고른 대표 사진. 못 골랐으면 null
         */
        public static Item of(
                long regionId,
                String sido,
                String sigungu,
                Coordinate coordinate,
                CrowdLevel crowdLevel,
                RegionContent content,
                String heroPhotoUrl) {
            // 이름을 붙여 조립한다 — 같은 타입이 붙어 있어 위치 생성자로는 둘을 맞바꿔도 컴파일이 통과한다.
            return Item.builder()
                    .regionId(regionId)
                    .sido(sido)
                    .sigungu(sigungu)
                    .coordinate(coordinate)
                    .crowdLevel(crowdLevel)
                    .imageUrl(heroPhotoUrl != null ? heroPhotoUrl : content.imageUrl())
                    .contentCount(content.contentCount())
                    .categories(content.categories())
                    .neighborIncluded(content.neighborIncluded())
                    .build();
        }
    }
}
