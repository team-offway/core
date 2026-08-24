package com.offway.core.trip.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.trip.controller.dto.HomeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;

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

                    **소유자는 access 토큰이 정한다**(#280). 예전에는 `X-Guest-Id` 헤더가 그 자리였는데,
                    서버가 검증할 수 없는 값이라 남의 연차를 볼 수 있었다. `remainingLeaveDays` 는
                    그 토큰이 가리키는 사용자의 값이다.

                    **access 토큰이 아닌 자격증명으로도 200 이다.** 홈은 소유 데이터 전용 경로가 아니라
                    403 으로 끊지 않는다 — 대신 주인을 못 정하므로 `remainingLeaveDays` 만 `null` 로 나가고
                    카드는 그대로 채워진다. 로그인 앞 화면이 통째로 비지 않게 하려는 것이다.
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공 (적재 전이면 카드가 빌 수 있다 — 502 아님. 주인을 못 정하면 remainingLeaveDays 만 null)")
    @ApiResponse(responseCode = "401", description = "자격증명이 아예 없음 — 모든 GET 이 인증 뒤에 있다(#122)")
    ApiResponseBody<HomeResponse> home(UUID userId);
}
