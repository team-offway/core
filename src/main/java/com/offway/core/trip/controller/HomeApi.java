package com.offway.core.trip.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.trip.controller.dto.HomeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;

/** 홈 API 문서 계약. 매핑은 구현체({@link HomeController})가 소유한다. */
@Tag(name = "홈", description = "남은 연차 · 필터칩 · 이번주 추천 지역")
public interface HomeApi {

    @Operation(
            summary = "홈",
            description =
                    """
                    남은 연차 + 필터칩 + 이번주 추천 지역(랭킹 top-N, 대표 이미지·categories·한산도·대표 혜택 뱃지).

                    **로그인은 선택이다.** access 토큰 없이 부르면 추천 지역·필터칩은 그대로 오고
                    `remainingLeaveDays` 만 null 이다 — 연차는 로그인한 사용자에게 묶여 있다.

                    **다만 지금은 자격증명 자체가 없으면 401 이다.** 서버 전체가 아직 인증 게이트 뒤에 있어
                    (#122) 익명 요청이 이 컨트롤러에 닿지 못한다. 위 "로그인 선택" 은 그 게이트가 열린 뒤의
                    계약이고, 그전까지는 Basic 자격증명으로 들어온 요청이 그 자리를 대신한다.
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공 (외부 관광정보 실패 시 이미지 없이 degrade — 502 아님)")
    @ApiResponse(responseCode = "401", description = "자격증명 없음 — 게이트(#122)가 열리기 전까지 익명 요청은 닿지 못한다")
    ApiResponseBody<HomeResponse> home(UUID userId);
}
