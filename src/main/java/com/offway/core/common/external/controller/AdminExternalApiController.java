package com.offway.core.common.external.controller;

import com.offway.core.common.external.ExternalApiStatusService;
import com.offway.core.common.external.controller.dto.ExternalApiStatusResponse;
import com.offway.core.common.response.ApiResponseBody;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 외부 API 연동 현황(#398).
 *
 * <p>권한은 {@code SecurityConfig} 의 {@code /api/v1/admin/**} 규칙이 건다 — 컨트롤러가 다시 걸지
 * 않는다.
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
}
