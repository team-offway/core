package com.offway.core.policy.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.common.response.PageResponse;
import com.offway.core.common.response.Paging;
import com.offway.core.policy.controller.dto.AdminPolicyRequest;
import com.offway.core.policy.controller.dto.AdminPolicyResponse;
import com.offway.core.policy.domain.Policy;
import com.offway.core.policy.service.PolicyAdminService;
import com.offway.core.user.config.LoginUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/policies")
@RequiredArgsConstructor
public class AdminPolicyController implements AdminPolicyApi {

    private final PolicyAdminService policyAdminService;

    @Override
    @GetMapping
    public ApiResponseBody<List<AdminPolicyResponse>> list(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        Page<Policy> found = policyAdminService.list(Paging.of(page, size));
        return ApiResponseBody.ok(AdminPolicyResponse.from(found.getContent()), PageResponse.from(found));
    }

    @Override
    @GetMapping("/{id}")
    public ApiResponseBody<AdminPolicyResponse> get(@PathVariable long id) {
        return ApiResponseBody.ok(AdminPolicyResponse.from(policyAdminService.get(id)));
    }

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponseBody<AdminPolicyResponse> create(
            @LoginUser UUID adminUserId, @Valid @RequestBody AdminPolicyRequest request) {
        return ApiResponseBody.created(
                AdminPolicyResponse.from(policyAdminService.create(request.toCommand(), adminUserId)));
    }

    @Override
    @PatchMapping("/{id}")
    public ApiResponseBody<AdminPolicyResponse> update(
            @LoginUser UUID adminUserId, @PathVariable long id, @Valid @RequestBody AdminPolicyRequest request) {
        return ApiResponseBody.ok(
                AdminPolicyResponse.from(policyAdminService.update(id, request.toCommand(), adminUserId)));
    }

    @Override
    @DeleteMapping("/{id}")
    public ApiResponseBody<Void> delete(@LoginUser UUID adminUserId, @PathVariable long id) {
        policyAdminService.delete(id, adminUserId);
        // 204 를 쓰지 않는다 — 응답 래퍼가 항상 body 를 만든다(exception-and-response).
        return ApiResponseBody.ok(null);
    }
}
