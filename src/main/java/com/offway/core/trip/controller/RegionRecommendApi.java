package com.offway.core.trip.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.trip.controller.dto.RegionRecommendRequest;
import com.offway.core.trip.controller.dto.RegionRecommendResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 여행지 추천 API 문서 계약. 매핑·검증은 구현체({@link RegionRecommendController})가 소유한다. */
@Tag(name = "여행지 추천", description = "도달 가능한 인구감소지역 추천")
public interface RegionRecommendApi {

    @Operation(
            summary = "후보 지역 추천",
            description = "도달 한계 안의 인구감소지역을 방문자 랭킹(덜 붐비는 로컬 우선)으로 추천한다. 볼거리 수·대표 이미지·categories(무드칩 분류)와"
                    + " 한산도·혜택 뱃지 포함. 무드칩 지정 시 해당 볼거리가 있는 지역을 앞세우고, 볼거리가 부족한 지역은 인접 50km 콘텐츠로 보강한다.")
    @ApiResponse(responseCode = "200", description = "추천 성공 (없으면 빈 목록. 외부 관광정보 실패 시 이미지·랭킹 가중치 없이 degrade — 502 아님)")
    @ApiResponse(responseCode = "400", description = "좌표 범위 초과 · 이동수단 누락 · 도달 한계가 양수가 아님")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "역할 없는 자격증명(Basic) — 소유자를 정할 수 없어 거절")
    ApiResponseBody<RegionRecommendResponse> recommend(RegionRecommendRequest request);
}
