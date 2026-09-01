package com.offway.core.curation.infrastructure.storage;

import java.time.Duration;
import java.util.Objects;
import lombok.Builder;

/**
 * 한 장을 올리기 위해 발급한 자리(#377).
 *
 * <p>둘을 함께 주는 이유는 <b>주소가 두 개</b>이기 때문이다. 올릴 때 쓰는 주소는 서명이 붙어 길고 곧
 * 만료되며, 저장해 둘 주소는 서명 없이 영구히 읽힌다. 앱이 나중에 보는 것은 뒤쪽이다.
 *
 * @param uploadUrl 브라우저가 {@code PUT} 할 서명된 주소 — <b>이 자체가 쓰기 권한</b>이라 로그에 남기지 않는다
 * @param publicUrl 저장해 둘 주소. 업로드가 끝나면 이 값이 {@code thumbnailUrl} 이 된다
 * @param expiresIn 서명이 살아 있는 시간 — 화면이 "다시 시도" 를 안내할 근거다
 */
@Builder
public record UploadTicket(String uploadUrl, String publicUrl, Duration expiresIn) {

    public UploadTicket {
        Objects.requireNonNull(uploadUrl, "업로드 주소는 null 일 수 없습니다.");
        Objects.requireNonNull(publicUrl, "공개 주소는 null 일 수 없습니다.");
        Objects.requireNonNull(expiresIn, "만료 시간은 null 일 수 없습니다.");
    }
}
