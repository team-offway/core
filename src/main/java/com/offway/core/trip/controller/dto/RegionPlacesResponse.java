package com.offway.core.trip.controller.dto;

import com.offway.core.common.logging.LogSummaries;
import com.offway.core.common.logging.LogSummary;
import com.offway.core.common.response.Attributed;
import com.offway.core.common.response.DataSource;
import com.offway.core.trip.domain.PlaceCategory;
import com.offway.core.trip.domain.PlaceKind;
import com.offway.core.trip.service.dto.RegionPlaces;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;

/**
 * 지역 장소 목록 응답(#144) — 숙소·맛집·카페·관광명소 탭 하나의 내용.
 *
 * @param kind 조회한 종류
 * @param kindLabel 종류 표시명(예: 숙소)
 * @param categories 이 종류에 있는 분류들 — 화면의 필터 칩 재료
 * @param places 이 페이지의 장소들
 */
@Schema(description = "지역 장소 목록")
public record RegionPlacesResponse(
        @Schema(description = "장소 종류", example = "STAY") PlaceKind kind,
        @Schema(description = "종류 표시명", example = "숙소") String kindLabel,
        @Schema(description = "이 종류의 분류 목록(필터 칩)") List<CategoryResponse> categories,
        @Schema(description = "장소 목록") List<PlaceResponse> places) implements LogSummary, Attributed {

    public static RegionPlacesResponse from(PlaceKind kind, RegionPlaces places) {
        return new RegionPlacesResponse(
                kind,
                kind.label(),
                PlaceCategory.of(kind).stream().map(CategoryResponse::from).toList(),
                places.places().stream().map(PlaceResponse::from).toList());
    }

    /**
     * 이 페이지에 실린 장소들의 출처만 담는다(#399).
     *
     * <p>고정 집합으로 두지 않는다 — 인허가 장소만 있는 페이지에 국가유산청을 붙이면 <b>안 쓴 출처를
     * 표기</b>하게 된다. 그것도 잘못된 표기다.
     */
    @Override
    public Set<DataSource> sources() {
        return PlaceDataSources.of(places, PlaceResponse::id);
    }

    @Override
    public String logSummary() {
        return LogSummaries.count(kindLabel, places);
    }

    /**
     * 필터 칩 하나.
     *
     * @param code 분류 코드(질의 파라미터로 그대로 되돌려 보낸다)
     * @param label 표시명
     */
    @Schema(description = "장소 분류")
    public record CategoryResponse(
            @Schema(example = "HANOK") PlaceCategory code,
            @Schema(example = "한옥체험") String label) {

        public static CategoryResponse from(PlaceCategory category) {
            return new CategoryResponse(category, category.label());
        }
    }

    /**
     * 목록의 장소 한 건. 사진·소개는 인허가 데이터에 없어 내려가지 않는다.
     *
     * @param id 상세 조회에 그대로 쓰는 식별자
     */
    @Schema(description = "장소")
    public record PlaceResponse(
            @Schema(description = "장소 식별자", example = "LIC-1234") String id,
            @Schema(description = "상호", example = "우경고택") String name,
            @Schema(description = "분류 코드", example = "HANOK") PlaceCategory category,
            @Schema(description = "분류 표시명", example = "한옥체험") String categoryLabel,
            @Schema(description = "도로명 주소") String address,
            @Schema(description = "대표 전화(없으면 null)", nullable = true) String tel,
            @Schema(description = "위도") double lat,
            @Schema(description = "경도") double lng) {

        public static PlaceResponse from(RegionPlaces.Place place) {
            return new PlaceResponse(
                    place.id(), place.name(), place.category(), place.categoryLabel(),
                    place.address(), place.tel(), place.lat(), place.lng());
        }
    }
}
