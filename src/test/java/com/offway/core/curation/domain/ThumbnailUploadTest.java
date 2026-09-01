package com.offway.core.curation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 올려도 되는 이미지인지 가리는 규칙(#377).
 *
 * <p>여기서 새면 <b>presigned URL 하나가 임의 파일을 올릴 수 있는 권한</b>이 된다. 발급은 성공으로 끝나고
 * 화면도 정상으로 보이므로, 새는 것을 알아채는 자리가 여기밖에 없다.
 */
class ThumbnailUploadTest {

    private static final long ONE_KB = 1024;

    @ParameterizedTest
    @CsvSource({"image/jpeg,jpg", "image/png,png", "image/webp,webp"})
    void 허용한_종류는_그에_맞는_확장자를_얻는다(String contentType, String extension) {
        ThumbnailUpload upload = ThumbnailUpload.of(contentType, ONE_KB);

        assertEquals(contentType, upload.contentType());
        assertEquals(extension, upload.extension());
    }

    /**
     * {@code image/} 로 시작하는지만 보면 통과했을 것들이다.
     *
     * <p>SVG 가 특히 위험하다 — 스크립트를 품을 수 있어 우리 도메인에서 열리는 순간 XSS 가 된다.
     */
    @ParameterizedTest
    @ValueSource(strings = {"image/svg+xml", "text/html", "application/pdf", "image/", "IMAGE/PNG", ""})
    void 허용하지_않는_종류는_거절한다(String contentType) {
        assertThrows(CurationException.class, () -> ThumbnailUpload.of(contentType, ONE_KB));
    }

    @Test
    void 종류가_없으면_거절한다() {
        assertThrows(CurationException.class, () -> ThumbnailUpload.of(null, ONE_KB));
    }

    @Test
    void 상한을_넘는_크기는_거절한다() {
        // 상한이 없으면 서명된 주소 하나로 버킷을 채울 수 있다.
        assertThrows(
                CurationException.class, () -> ThumbnailUpload.of("image/png", ThumbnailUpload.MAX_BYTES + 1));
    }

    @Test
    void 상한과_같은_크기는_통과한다() {
        // 경계를 배타로 두면 "5MB 까지" 라고 안내해 놓고 정확히 5MB 를 거절한다.
        assertEquals(ThumbnailUpload.MAX_BYTES, ThumbnailUpload.of("image/png", ThumbnailUpload.MAX_BYTES)
                .contentLength());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void 크기가_0_이하면_거절한다(long contentLength) {
        // 0 을 서명에 실으면 빈 오브젝트가 올라가고, 화면에는 깨진 이미지가 남는다.
        assertThrows(CurationException.class, () -> ThumbnailUpload.of("image/png", contentLength));
    }

    @Test
    void 오브젝트_키는_원본_이름이_아니라_id_와_확장자로_만든다() {
        // 원본 파일명에는 한글·공백·경로 문자가 섞여 온다. 애초에 이름을 받지 않는다.
        UUID id = UUID.fromString("8f14e45f-ceea-467a-9d3d-1234567890ab");

        String key = ThumbnailUpload.of("image/webp", ONE_KB).objectKey(id);

        assertEquals("curation/8f14e45f-ceea-467a-9d3d-1234567890ab.webp", key);
    }

    @Test
    void 오브젝트_키는_이_기능이_만든_것끼리_모인다() {
        assertTrue(ThumbnailUpload.of("image/png", ONE_KB).objectKey(UUID.randomUUID()).startsWith("curation/"));
    }
}
