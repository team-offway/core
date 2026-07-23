package com.offway.core.policy.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.policy.controller.dto.PolicyResponse;
import com.offway.core.policy.service.PolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
public class PolicyController implements PolicyApi {

    private final PolicyService policyService;

    @Override
    @GetMapping("/{policyId}")
    public ApiResponseBody<PolicyResponse> getPolicy(@PathVariable Long policyId) {
        return ApiResponseBody.ok(PolicyResponse.from(policyService.getPolicyDetail(policyId)));
    }
}
