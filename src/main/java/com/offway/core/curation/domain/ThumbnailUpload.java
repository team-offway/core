package com.offway.core.curation.domain;

import java.util.Map;
import java.util.UUID;

/**
 * 백오피스가 올리려는 썸네일 한 장(#377) — <b>올리기 전에</b> 무엇을 허용할지 정한 결과.
 *
 * <p>presigned URL 은 발급하는 순간 그 자체로 <b>쓰기 권한</b>이다. 조건 없이 내주면 URL 하나로 임의
 * 파일을 임의 크기로 올릴 수 있다. 그래서 종류와 크기를 여기서 먼저 가르고, 통과한 값만 서명에 싣는다.
 *
 * <p><b>확장자를 파일명이 아니라 종류에서 뽑는다.</b> 원본 파일명은 한글·공백·경로 문자가 섞여 오고,
 * 그대로 키에 쓰면 나중에 URL 인코딩이 어긋나는 자리가 생긴다. 어차피 신뢰할 값은 서명에 싣는
 * {@code contentType} 이라, 이름은 받지 않는다.
 *
 * @param contentType 서명에 실을 종류 — S3 가 이 값과 다른 요청을 거절한다
 * @param extension 오브젝트 키의 꼬리
 * @param contentLength 서명에 실을 바이트 수 — 이 값과 다른 크기를 올리면 S3 가 거절한다
 */
public record ThumbnailUpload(String contentType, String extension, long contentLength) {

    /**
     * 허용하는 이미지 종류와 그에 대응하는 확장자.
     *
     * <p><b>화이트리스트다.</b> {@code image/} 로 시작하는지만 보면 {@code image/svg+xml} 이 통과하는데,
     * SVG 는 스크립트를 품을 수 있어 우리 도메인에서 열리는 순간 XSS 가 된다.
     */
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    /** 오브젝트 키 앞머리 — 버킷 안에서 이 기능이 만든 것만 모아 둔다. */
    private static final String KEY_PREFIX = "curation/";

    /**
     * 한 장의 크기 상한(5MB).
     *
     * <p>썸네일은 카드 안에서 몇백 px 로 그려진다. 원본을 그대로 올려도 이 정도면 충분하고, 넘는 것은
     * 줄이지 않고 올린 것이라 봐도 된다. 상한이 없으면 presigned URL 하나로 버킷을 채울 수 있다.
     */
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    /**
     * 올릴 수 있는 값인지 가려 받는다.
     *
     * @throws CurationException 허용하지 않는 종류이거나, 크기가 0 이하이거나 상한을 넘는 경우
     */
    public static ThumbnailUpload of(String contentType, long contentLength) {
        // 종류가 없는 것도 "허용하지 않는 종류" 다. Map.of 는 null 키 조회에서 NPE 를 던지므로 먼저 끊는다 —
        // 그대로 두면 입력 실수가 계약 위반(400)이 아니라 서버 오류(500)로 나간다.
        if (contentType == null) {
            throw CurationException.unsupportedImageType();
        }
        String extension = ALLOWED_TYPES.get(contentType);
        if (extension == null) {
            throw CurationException.unsupportedImageType();
        }
        if (contentLength <= 0 || contentLength > MAX_BYTES) {
            throw CurationException.imageTooLarge();
        }
        return new ThumbnailUpload(contentType, extension, contentLength);
    }

    /**
     * 저장할 오브젝트 키. 이름이 겹쳐 앞의 것을 덮어쓰지 않도록 <b>매번 새 id</b> 를 쓴다.
     *
     * @param id 이 업로드의 식별자 — 무작위여야 남의 썸네일 주소를 추측할 수 없다
     */
    public String objectKey(UUID id) {
        return KEY_PREFIX + id + "." + extension;
    }
}
