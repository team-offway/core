package com.offway.core.trip.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.trip.controller.dto.PoiDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 장소(POI) 상세 API 문서 계약. 매핑은 구현체({@link PoiController})가 소유한다. */
@Tag(name = "장소", description = "관광지·맛집·숙박 등 장소 상세")
public interface PoiApi {

    @Operation(summary = "장소 상세", description = "장소의 기본정보(이름·주소·이미지·소개)와 운영시간·휴무일을 합쳐 내린다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "요청한 장소를 관광정보에서 찾을 수 없음")
    @ApiResponse(responseCode = "502", description = "관광정보(TourAPI) 조회 실패")
    ApiResponseBody<PoiDetailResponse> detail(@Parameter(description = "TourAPI 콘텐츠 ID", example = "126508") String contentId);
}
