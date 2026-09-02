package com.offway.core.common.external.controller;

import com.offway.core.common.external.controller.dto.BatchSettingRequest;
import com.offway.core.common.external.controller.dto.ExternalApiSettingRequest;
import com.offway.core.common.external.controller.dto.ExternalApiStatusResponse;
import com.offway.core.common.response.ApiResponseBody;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;

@Tag(name = "어드민 - 외부 API")
public interface AdminExternalApiApi {

    @Operation(
            summary = "외부 API 연동 현황",
            description = """
                    우리가 붙어 있는 외부 API 전부의 사용량·주체·데이터 출처를 한 번에 돌려준다.

                    `/api/v1/quotas` 와 다른 점은 **기간**이다. 그쪽은 오늘치뿐이라 어제를 못 보고,
                    월배치가 튀는 날을 가려낼 수 없다.
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "401", description = "로그인하지 않았거나 토큰이 만료됨")
    @ApiResponse(responseCode = "403", description = "어드민 권한이 없음")
    ApiResponseBody<ExternalApiStatusResponse> status(
            @Parameter(description = "오늘부터 거슬러 셀 일수. 기본 14, 상한 90. 벗어나면 거절하지 않고 자른다")
            Integer days);

    @Operation(
            summary = "연동 설정 변경",
            description = """
                    캐시 사용 여부와 배치 하루 상한을 바꾼다. **배포 없이 그 자리에서 반영된다.**

                    캐시를 끄면 그 연동은 매번 실호출한다 — 값이 늘 최신이 되는 대신 한도를 그만큼
                    더 태운다. 배치 상한은 그 API 의 **오늘 총 사용량**과 견주므로, 사용자가 이미
                    많이 썼으면 배치가 더 일찍 물러난다.
                    """)
    @ApiResponse(responseCode = "200", description = "변경 성공")
    @ApiResponse(responseCode = "400", description = "모르는 연동이거나, 상한이 음수·일일 한도 초과")
    @ApiResponse(responseCode = "401", description = "로그인하지 않았거나 토큰이 만료됨")
    @ApiResponse(responseCode = "403", description = "어드민 권한이 없음")
    ApiResponseBody<ExternalApiStatusResponse> updateApi(
            UUID adminUserId,
            @Parameter(description = "ExternalApi 이름", example = "TOUR_API") String api,
            ExternalApiSettingRequest request);

    @Operation(
            summary = "배치 켜기·끄기",
            description = """
                    끄면 주기가 와도 돌지 않는다. 건너뛴 사실은 로그에 남는다 — 조용히 넘기면
                    "왜 안 채워지지" 가 된다.
                    """)
    @ApiResponse(responseCode = "200", description = "변경 성공")
    @ApiResponse(responseCode = "401", description = "로그인하지 않았거나 토큰이 만료됨")
    @ApiResponse(responseCode = "403", description = "어드민 권한이 없음")
    ApiResponseBody<ExternalApiStatusResponse> updateBatch(
            UUID adminUserId,
            @Parameter(description = "batch_run.name", example = "poi-intro-refresh") String name,
            BatchSettingRequest request);
}
