package com.offway.core.curation.controller.dto;

import com.offway.core.curation.infrastructure.storage.UploadTicket;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 썸네일 업로드 자리(#377).
 *
 * <p>주소가 둘인 이유는 쓰임이 다르기 때문이다 — 올릴 때 쓰는 것은 서명이 붙어 곧 만료되고, 저장해 둘
 * 것은 서명 없이 영구히 읽힌다.
 *
 * @param uploadUrl 여기에 {@code PUT} 으로 파일을 올린다. 요청의 {@code Content-Type} 과 크기가 서명한
 *     값과 달라지면 S3 가 거절한다
 * @param publicUrl 업로드가 끝나면 이 값을 {@code thumbnailUrl} 로 저장한다
 * @param expiresInSeconds 서명이 살아 있는 시간(초). 넘기면 다시 발급받아야 한다
 */
@Builder
public record ThumbnailUploadResponse(
        @Schema(example = "https://bucket.s3.ap-northeast-2.amazonaws.com/curation/....?X-Amz-Signature=...")
                String uploadUrl,
        @Schema(example = "https://bucket.s3.ap-northeast-2.amazonaws.com/curation/8f14e45f.png") String publicUrl,
        @Schema(example = "300") long expiresInSeconds) {

    /**
     * 이름을 붙여 옮긴다. 두 주소가 나란히 선 {@code String} 이라 위치로 넘기면 맞바꿔도 컴파일이
     * 통과하는데, 그러면 <b>곧 만료될 서명된 주소가 {@code thumbnailUrl} 로 저장된다</b> — 저장 직후에는
     * 멀쩡히 보이다가 서명이 죽는 순간 카드가 깨진다.
     */
    public static ThumbnailUploadResponse from(UploadTicket ticket) {
        return ThumbnailUploadResponse.builder()
                .uploadUrl(ticket.uploadUrl())
                .publicUrl(ticket.publicUrl())
                .expiresInSeconds(ticket.expiresIn().toSeconds())
                .build();
    }
}
