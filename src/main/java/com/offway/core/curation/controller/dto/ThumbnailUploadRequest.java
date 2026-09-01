package com.offway.core.curation.controller.dto;

import com.offway.core.curation.domain.ThumbnailUpload;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * 썸네일 업로드 자리 요청(#377).
 *
 * <p><b>파일명을 받지 않는다.</b> 오브젝트 키의 확장자는 {@code contentType} 에서 뽑는다 — 원본 이름에는
 * 한글·공백·경로 문자가 섞여 오고, 그대로 키에 쓰면 나중에 URL 인코딩이 어긋나는 자리가 생긴다. 받아 두고
 * 안 쓰면 다음 사람이 그 값이 키에 들어간다고 읽으므로 아예 계약에서 뺐다.
 *
 * @param contentType 올릴 이미지 종류 — 이 값이 그대로 서명에 실려 S3 가 다른 요청을 거절한다
 * @param contentLength 올릴 바이트 수 — 위와 같은 이유로 서명에 실린다. 브라우저는 {@code File.size} 를 안다
 */
public record ThumbnailUploadRequest(
        @Schema(example = "image/png", description = "image/jpeg · image/png · image/webp 만 허용")
                @NotBlank(message = "이미지 종류는 비울 수 없습니다.")
                String contentType,
        @Schema(example = "204800", description = "바이트. 5MB 를 넘으면 400")
                @Positive(message = "이미지 크기는 0보다 커야 합니다.")
                long contentLength) {

    /** 종류·크기 검증은 도메인이 한다 — 여기서는 값을 옮기기만 한다. */
    public ThumbnailUpload toUpload() {
        return ThumbnailUpload.of(contentType, contentLength);
    }
}
