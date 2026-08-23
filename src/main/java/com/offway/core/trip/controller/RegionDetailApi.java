package com.offway.core.trip.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.trip.controller.dto.RegionDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "지역")
public interface RegionDetailApi {

    @Operation(
            summary = "지역 상세",
            description =
                    """
                    지역 소개 + 대표 이미지 + **매력 포인트 장소**를 한 번에 준다. 화면 하나가 이 응답으로 채워진다.

                    **`highlightSpots` 는 사진 있는 장소만 담는다.** 사진 없는 항목이 섞이면 가로 목록 중간에
                    회색 판이 낀다. 그 지역에 사진 있는 장소가 적으면 목록도 짧게 온다 — 최대 10개이고,
                    **최소는 보장하지 않는다.** 억지로 채우려면 사진 없는 것을 섞어야 하기 때문이다.

                    누르면 `GET /api/v1/pois/{poiContentId}` 로 그대로 이어진다.

                    **`overview` 는 한 줄이다.** TourAPI 에 지역 소개 엔드포인트가 없어 그 지역에 실제로 있는
                    것의 이름으로 만든 문장이다. 재료가 없는 지역은 `null` 이라 그 칸을 접으면 된다.

                    **외부 API 를 부르지 않는다.** 장소 풀은 월 1회 배치가 미리 채워 두고 여기서는 DB 만 읽는다.
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "404", description = "없는 지역(TRIP-002)")
    ApiResponseBody<RegionDetailResponse> detail(
            @Parameter(description = "지역 ID", example = "1") Long regionId);
}
