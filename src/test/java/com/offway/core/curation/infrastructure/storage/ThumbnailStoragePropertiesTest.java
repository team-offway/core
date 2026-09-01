package com.offway.core.curation.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 저장소 좌표가 "쓸 수 있는 상태인가" 를 가리는 규칙(#377).
 *
 * <p>여기서 틀리면 업로드는 성공하고 <b>그 뒤로 이미지만 안 열린다</b> — 저장까지 끝난 다음에 드러나는
 * 종류라 되돌리기가 번거롭다.
 */
class ThumbnailStoragePropertiesTest {

    private static ThumbnailStorageProperties of(String bucket, String publicBaseUrl) {
        return new ThumbnailStorageProperties(bucket, "ap-northeast-2", "AKIA", "secret", publicBaseUrl);
    }

    @Test
    void 넷이_다_있어야_서명할_수_있다() {
        assertTrue(of("offway-assets", null).isConfigured());
    }

    @Test
    void 하나라도_비면_서명하지_않는다() {
        // 반쯤 채워 두면 서명은 만들어지고 S3 가 거절한다 — 어드민이 올리기를 누른 뒤에야 실패를 안다.
        assertFalse(new ThumbnailStorageProperties(null, "ap-northeast-2", "AKIA", "secret", null).isConfigured());
        assertFalse(new ThumbnailStorageProperties("offway-assets", "  ", "AKIA", "secret", null).isConfigured());
        assertFalse(new ThumbnailStorageProperties("offway-assets", "ap-northeast-2", null, "secret", null)
                .isConfigured());
        assertFalse(new ThumbnailStorageProperties("offway-assets", "ap-northeast-2", "AKIA", null, null)
                .isConfigured());
    }

    @Test
    void 기본_공개_주소는_버킷과_리전에서_만든다() {
        assertEquals(
                "https://offway-assets.s3.ap-northeast-2.amazonaws.com",
                of("offway-assets", null).publicBaseUrlOrDefault());
    }

    @Test
    void 따로_준_주소가_있으면_그것을_쓴다() {
        // CloudFront 를 앞에 두면 버킷 주소와 달라진다.
        assertEquals(
                "https://cdn.offway.cloud",
                of("offway-assets", "https://cdn.offway.cloud").publicBaseUrlOrDefault());
    }

    @Test
    void 따로_준_주소의_꼬리_슬래시는_지운다() {
        // 안 지우면 오브젝트 키를 붙일 때 슬래시가 둘이 된다.
        assertEquals(
                "https://cdn.offway.cloud",
                of("offway-assets", "https://cdn.offway.cloud/").publicBaseUrlOrDefault());
    }

    @Test
    void 점이_든_버킷은_기본_주소를_만들_수_없다() {
        // virtual-hosted 형식이라 호스트명이 *.s3.리전.amazonaws.com 와일드카드 인증서와 안 맞는다.
        assertFalse(of("offway.assets", null).canDeriveDefaultUrl());
    }

    @Test
    void 점이_들었어도_주소를_따로_주면_괜찮다() {
        assertTrue(of("offway.assets", "https://cdn.offway.cloud").canDeriveDefaultUrl());
    }
}
