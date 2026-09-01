package com.offway.core.curation.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.curation.controller.dto.ThumbnailUploadRequest;
import com.offway.core.curation.controller.dto.ThumbnailUploadResponse;
import com.offway.core.curation.service.ThumbnailUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/uploads")
@RequiredArgsConstructor
public class AdminUploadController implements AdminUploadApi {

    private final ThumbnailUploadService thumbnailUploadService;

    @Override
    @PostMapping
    public ApiResponseBody<ThumbnailUploadResponse> issue(@Valid @RequestBody ThumbnailUploadRequest request) {
        // 201 이 아니라 200 이다 — 만들어진 것은 자리(서명)일 뿐이고, 오브젝트는 아직 없다.
        return ApiResponseBody.ok(
                ThumbnailUploadResponse.from(thumbnailUploadService.issue(request.toUpload())));
    }
}
