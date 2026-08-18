package com.offway.core.common.external.controller;

import com.offway.core.common.external.ExternalApiCallRecorder;
import com.offway.core.common.external.controller.dto.QuotaResponse;
import com.offway.core.common.response.ApiResponseBody;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quotas")
@RequiredArgsConstructor
public class QuotaController implements QuotaApi {

    private final ExternalApiCallRecorder callRecorder;

    @Override
    @GetMapping
    public ApiResponseBody<QuotaResponse> quotas() {
        return ApiResponseBody.ok(QuotaResponse.from(callRecorder.snapshotToday()));
    }
}
