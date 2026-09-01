package com.offway.core.curation.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 썸네일 저장소(S3) 좌표(#377). <b>값이 없어도 부팅된다</b> — 업로드 주소 발급만 실패한다.
 *
 * <p>키를 부팅 조건으로 만들면 로컬·CI 가 전부 막힌다. 키 없이도 뜬다는 것이 이 프로젝트의 불변식이고
 * TourAPI·TMAP·FCM 이 이미 그 방식이다(CLAUDE.md 로컬 실행성).
 *
 * @param bucket 버킷 이름
 * @param region 버킷 리전(예: {@code ap-northeast-2})
 * @param accessKey 업로드 주소를 서명할 IAM 자격증명
 * @param secretKey 위와 짝
 * @param publicBaseUrl 저장해 둘 주소의 앞머리. CloudFront 같은 것을 앞에 두면 버킷 주소와 달라지므로
 *     따로 받는다. 비우면 버킷의 기본 주소를 쓴다
 */
@ConfigurationProperties(prefix = "offway.storage.s3")
public record ThumbnailStorageProperties(
        String bucket, String region, String accessKey, String secretKey, String publicBaseUrl) {

    /**
     * 서명할 수 있는 상태인가 — 넷이 <b>모두</b> 있어야 한다.
     *
     * <p>하나라도 비면 서명은 만들어지지만 S3 가 거절한다. 그 실패는 업로드를 누른 뒤에야 브라우저에서
     * 드러나므로, 발급 단계에서 미리 끊어 "저장소를 쓸 수 없다" 로 답하는 편이 낫다.
     */
    public boolean isConfigured() {
        return hasText(bucket) && hasText(region) && hasText(accessKey) && hasText(secretKey);
    }

    /** 저장해 둘 주소의 앞머리 — 따로 주지 않았으면 버킷의 기본 주소. */
    public String publicBaseUrlOrDefault() {
        if (hasText(publicBaseUrl)) {
            return publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
        }
        return "https://%s.s3.%s.amazonaws.com".formatted(bucket, region);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
