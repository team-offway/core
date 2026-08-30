package com.offway.core.curation.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.common.response.PageResponse;
import com.offway.core.common.response.Paging;
import com.offway.core.curation.controller.dto.AdminCuratedLinkRequest;
import com.offway.core.curation.controller.dto.AdminCuratedLinkResponse;
import com.offway.core.curation.domain.CuratedLink;
import com.offway.core.curation.service.CurationAdminService;
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
@RequestMapping("/api/v1/admin/curated-links")
@RequiredArgsConstructor
public class AdminCuratedLinkController implements AdminCuratedLinkApi {

    private final CurationAdminService curationAdminService;

    @Override
    @GetMapping
    public ApiResponseBody<List<AdminCuratedLinkResponse>> list(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        Page<CuratedLink> found = curationAdminService.list(Paging.of(page, size));
        return ApiResponseBody.ok(AdminCuratedLinkResponse.from(found.getContent()), PageResponse.from(found));
    }

    @Override
    @GetMapping("/{id}")
    public ApiResponseBody<AdminCuratedLinkResponse> get(@PathVariable long id) {
        return ApiResponseBody.ok(AdminCuratedLinkResponse.from(curationAdminService.get(id)));
    }

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponseBody<AdminCuratedLinkResponse> create(
            @LoginUser UUID adminUserId, @Valid @RequestBody AdminCuratedLinkRequest request) {
        return ApiResponseBody.created(
                AdminCuratedLinkResponse.from(curationAdminService.create(request.toCommand(), adminUserId)));
    }

    @Override
    @PatchMapping("/{id}")
    public ApiResponseBody<AdminCuratedLinkResponse> update(
            @LoginUser UUID adminUserId,
            @PathVariable long id,
            @Valid @RequestBody AdminCuratedLinkRequest request) {
        return ApiResponseBody.ok(
                AdminCuratedLinkResponse.from(curationAdminService.update(id, request.toCommand(), adminUserId)));
    }

    @Override
    @DeleteMapping("/{id}")
    public ApiResponseBody<Void> delete(@LoginUser UUID adminUserId, @PathVariable long id) {
        curationAdminService.delete(id, adminUserId);
        // 204 를 쓰지 않는다 — 응답 래퍼가 항상 body 를 만든다(exception-and-response).
        return ApiResponseBody.ok(null);
    }
}
