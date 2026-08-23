package com.offway.core.trip.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.trip.controller.dto.HomeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 홈 API 문서 계약. 매핑은 구현체({@link HomeController})가 소유한다. */
@Tag(name = "홈", description = "남은 연차 · 필터칩 · 이번달 추천 여행지(장소) · 이번 연차엔 여기 어때요(지역)")
public interface HomeApi {

    @Operation(
            summary = "홈",
            description =
                    """
                    남은 연차 + 필터칩 + 카드 두 묶음.

                    **`recommendedPlaces`** — 시안 "이번달 추천 여행지". 카드가 **장소**이고 칩 필터가 걸린다.
                    `kind` 로 앱이 거른다. `subtitle` 은 카테고리마다 다른 값에서 서버가 조립하며,
                    재료가 없으면 `null` 이라 앱이 그 줄을 접는다.

                    **`recommendedRegions`** — 시안 "이번 연차엔 여기 어때요?". 카드가 **지역**이고
                    지역명·대표 이미지·한산도·혜택을 쓴다.

                    둘 다 DB 만 읽는다 — 외부 관광정보를 요청 경로에서 부르지 않는다.
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공 (적재 전이면 카드가 빌 수 있다 — 502 아님)")
    ApiResponseBody<HomeResponse> home(
            @Parameter(description = "소유 키 헤더 (없으면 남은 연차는 null)", example = "guest-abc123")
                    String guestId);
}
