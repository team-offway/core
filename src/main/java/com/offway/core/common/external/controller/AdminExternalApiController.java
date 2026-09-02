package com.offway.core.common.external.controller;

import com.offway.core.common.external.ExternalApiStatusService;
import com.offway.core.common.external.controller.dto.BatchSettingRequest;
import com.offway.core.common.external.controller.dto.ExternalApiSettingRequest;
import com.offway.core.common.external.controller.dto.ExternalApiStatusResponse;
import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.user.config.LoginUser;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 외부 API 연동 현황과 제어(#398 · #403).
 *
 * <p>권한은 {@code SecurityConfig} 의 {@code /api/v1/admin/**} 규칙이 건다 — 컨트롤러가 다시 걸지
 * 않는다.
 *
 * <p>바꾼 뒤에도 <b>현황 전체를 돌려준다.</b> 어드민이 스위치를 내리면 그 즉시 예상 콜 수와 소진율이
 * 달라지는데, 응답이 바뀐 한 줄만 주면 화면이 나머지를 다시 물어야 한다.
 */
@RestController
@RequestMapping("/api/v1/admin/external-apis")
@RequiredArgsConstructor
public class AdminExternalApiController implements AdminExternalApiApi {

    private final ExternalApiStatusService statusService;

    @Override
    @GetMapping
    public ApiResponseBody<ExternalApiStatusResponse> status(
            @RequestParam(required = false) Integer days) {
        return ApiResponseBody.ok(ExternalApiStatusResponse.from(statusService.snapshot(days)));
    }

    @Override
    @PatchMapping("/{api}")
    public ApiResponseBody<ExternalApiStatusResponse> updateApi(
            @LoginUser UUID adminUserId,
            @PathVariable String api,
            @RequestBody ExternalApiSettingRequest request) {
        return ApiResponseBody.ok(ExternalApiStatusResponse.from(
                statusService.updateApi(api, request, adminUserId)));
    }

    @Override
    @PatchMapping("/batches/{name}")
    public ApiResponseBody<ExternalApiStatusResponse> updateBatch(
            @LoginUser UUID adminUserId,
            @PathVariable String name,
            @RequestBody BatchSettingRequest request) {
        return ApiResponseBody.ok(ExternalApiStatusResponse.from(
                statusService.updateBatch(name, request, adminUserId)));
    }
}
