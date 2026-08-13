package com.offway.core.trip.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.trip.controller.dto.RegionPlacesResponse;
import com.offway.core.trip.domain.PlaceCategory;
import com.offway.core.trip.domain.PlaceKind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "지역 장소")
public interface RegionPlaceApi {

    @Operation(
            summary = "지역의 장소 목록",
            description = """
                    인구감소지역 89곳의 숙소·맛집·카페·관광명소를 종류별로 조회한다.

                    지방행정인허가 데이터를 부팅 시 적재해 쓰므로 **외부 API 호출이 없다** — 관광 API 한도가
                    소진되거나 포털이 점검 중이어도 목록은 그대로 나간다. 영업 중인 곳만 실려 있고,
                    술집·편의점처럼 여행지로 볼 수 없는 업태는 제외돼 있다.

                    사진·소개는 인허가 데이터에 없어 내려가지 않는다. 대신 전화번호가 있는 곳이 있다.
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = "분류가 종류에 속하지 않음(예: 숙소 탭에 카페 분류)")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    ApiResponseBody<RegionPlacesResponse> places(
            Long regionId, PlaceKind kind, PlaceCategory category, Integer page, Integer size);
}
