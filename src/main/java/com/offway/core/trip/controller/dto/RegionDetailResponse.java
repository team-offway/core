package com.offway.core.trip.controller.dto;

import com.offway.core.curation.controller.dto.CuratedLinkResponse;
import com.offway.core.curation.domain.CuratedLink;
import com.offway.core.common.response.Attributed;
import com.offway.core.common.response.DataSource;
import com.offway.core.policy.domain.PolicyType;
import com.offway.core.trip.service.dto.HomeResult;
import com.offway.core.trip.service.dto.RegionDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 지역 상세 응답(#304).
 *
 * @param name 화면에 그대로 쓰는 표기 — {@code 시군구 · 시도}. 조합을 여기서 하는 이유는 서비스가 화면
 *     문구를 들지 않기 위해서다(홈·추천 응답과 같은 방식)
 * @param overview 지역 한 줄 소개. <b>null 이면 앱이 그 칸을 접는다.</b> 3줄 문단이 아니라 한 줄이다 —
 *     TourAPI 에 지역 소개 엔드포인트가 없어 사실만 조합해 만든 값이다(#140)
 * @param photos 대표 이미지. 지금은 최대 한 장이고, 없으면 <b>빈 목록</b>이다(null 이 아니다)
 * @param highlightSpots 매력 포인트 장소 — <b>사진 있는 것만</b> 담긴다. 사진 없는 항목이 섞이면 가로
 *     목록 중간에 회색 판이 낀다. 그 지역에 사진 있는 장소가 적으면 목록도 짧다
 * @param curatedLinks 외부 페이지로 나가는 창구(#341). 지역 상세에 켜진 것만, 정렬 순으로.
 *     없으면 <b>빈 목록</b>이다
 */
public record RegionDetailResponse(
        @Schema(example = "1") long regionId,
        @Schema(example = "동구 · 부산광역시") String name,
        @Schema(example = "금수사와 정공단이 있는 곳", nullable = true) String overview,
        List<String> photos,
        @Schema(nullable = true) Benefit benefit,
        List<HighlightSpot> highlightSpots,
        List<CuratedLinkResponse> curatedLinks) implements Attributed {

    /**
     * 이 화면의 값은 <b>거의 다 공사 것</b>이다(#399) — 소개·대표 사진·매력 포인트 장소와 그 캐치프레이즈.
     *
     * <p>매력 포인트는 인허가·국가유산 장소가 섞일 수 있어 실린 것만 센다. 지역명·혜택은 우리가 만든
     * 값이라 출처가 없다.
     */
    @Override
    public Set<DataSource> sources() {
        Set<DataSource> spotSources = PlaceDataSources.of(highlightSpots, HighlightSpot::poiContentId);
        if (overview == null && photos.isEmpty()) {
            return spotSources;
        }
        // 소개나 대표 사진이 있으면 그 자체가 공사 값이다 — 장소가 하나도 없어도 표기가 필요하다.
        return Stream.concat(spotSources.stream(), Stream.of(DataSource.KTO))
                .collect(Collectors.toUnmodifiableSet());
    }

    public static RegionDetailResponse from(RegionDetail detail, List<CuratedLink> curatedLinks) {
        return new RegionDetailResponse(
                detail.regionId(),
                detail.sigungu() + " · " + detail.sido(),
                detail.overview(),
                detail.photos(),
                detail.benefit() == null ? null : Benefit.from(detail.benefit()),
                detail.highlightSpots().stream().map(HighlightSpot::from).toList(),
                CuratedLinkResponse.from(curatedLinks));
    }

    /**
     * 혜택 뱃지 — <b>필드 이름·순서를 홈 응답과 같게 둔다</b>. 같은 값이 두 화면에서 다른 모양으로 오면
     * 앱이 화면마다 다른 파서를 들어야 한다.
     *
     * @param policyId 누르면 이 혜택의 상세로 간다
     */
    public record Benefit(
            @Schema(example = "숙박 할인") String text,
            @Schema(example = "STAY_FESTA") PolicyType policyType,
            @Schema(example = "2") long policyId) {

        static Benefit from(HomeResult.Benefit benefit) {
            return new Benefit(benefit.text(), benefit.type(), benefit.policyId());
        }
    }

    /**
     * @param poiContentId 누르면 {@code GET /api/v1/pois/{poiContentId}} 로 그대로 이어진다
     * @param catchphrase 구석구석 한 줄 소개. <b>null 일 수 있다</b> — 그 장소가 캐치프레이즈 목록에 없으면
     *     비고, 앱은 이름만 그린다
     */
    public record HighlightSpot(
            @Schema(example = "2708108") String poiContentId,
            @Schema(example = "범일 이중섭거리") String name,
            @Schema(example = "http://tong.visitkorea.or.kr/cms/resource/71/3552571_image2_1.jpg") String imageUrl,
            @Schema(example = "범일동 풍경을 그린 천재 화가 이중섭", nullable = true) String catchphrase) {

        static HighlightSpot from(RegionDetail.Spot spot) {
            return new HighlightSpot(spot.poiContentId(), spot.name(), spot.imageUrl(), spot.catchphrase());
        }
    }
}
