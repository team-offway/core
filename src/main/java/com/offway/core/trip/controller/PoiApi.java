package com.offway.core.trip.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.trip.controller.dto.AccessibilityResponse;
import com.offway.core.trip.controller.dto.PoiDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 장소(POI) 상세 API 문서 계약. 매핑은 구현체({@link PoiController})가 소유한다. */
@Tag(name = "장소", description = "관광지·맛집·숙박 등 장소 상세")
public interface PoiApi {

    @Operation(
            summary = "장소 상세",
            description = """
                    장소의 기본정보(이름·주소·이미지·소개)에 **카테고리별 보조정보**를 합쳐 내린다.

                    보조정보는 카테고리마다 블록이 다르고, 해당 없는 블록은 null 이다.
                    - `sight`(관광지) 이용시간·휴무일·주차
                    - `culture`(문화시설) 이용시간·휴무일·요금·주차
                    - `leports`(레포츠) 이용시간·휴무일·요금·주차
                    - `food`(음식점) 영업시간·휴무일·대표메뉴·취급메뉴
                    - `stay`(숙박) 입실·퇴실·객실수·예약안내

                    우리 DB 에서 온 장소(`LIC-`·`HER-` 접두어)는 보조정보가 없어 모든 블록이 null 이다. 

                    대신 `mapSearchUrl` 이 실린다 — 영업시간·사진을 우리가 못 주므로 지도 검색으로 넘긴다.""")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "요청한 장소를 관광정보에서 찾을 수 없음")
    @ApiResponse(responseCode = "502", description = "관광정보(TourAPI) 조회 실패")
    ApiResponseBody<PoiDetailResponse> detail(@Parameter(description = "TourAPI 콘텐츠 ID", example = "126508") String contentId);

    @Operation(
            summary = "장소 무장애(배리어프리) 정보",
            description = "이용약자 편의(주차·휠체어·점자·수어·수유실 등)를 분류별로 내린다. 등록 정보가 없으면 빈 배열로 200.")
    @ApiResponse(responseCode = "200", description = "조회 성공(등록 정보 없으면 빈 배열)")
    @ApiResponse(responseCode = "502", description = "관광정보(TourAPI) 조회 실패")
    ApiResponseBody<AccessibilityResponse> accessibility(
            @Parameter(description = "TourAPI 콘텐츠 ID", example = "126508") String contentId);
}
