package com.offway.core.trip.infrastructure.gallery;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 갤러리 검색 URI 조립(#535).
 *
 * <p><b>이 테스트가 없어서 조용히 죽어 있었다.</b> 운영에서 교통 거점 사진 배치가 4주 동안 갤러리를 한 번도
 * 못 부르고 155곳을 "사진 없음" 으로 적었는데, 원인은 응답도 네트워크도 아닌 <b>URI 를 만들다 나는 예외</b>
 * 였다. 그 예외가 호출 직전의 한도 기록보다 앞이라 호출 기록에도 안 남았다.
 */
@DisplayName("갤러리 검색 URI")
class GalleryPhotoClientUriTest {

    /** 이미 퍼센트 인코딩된 공공데이터 키 — 이 값 때문에 build(true) 를 쓸 수밖에 없다(#165). */
    private static final String ENCODED_KEY = "abc%2Bdef%3D%3D";

    @ParameterizedTest(name = "{0} 로도 URI 를 만든다")
    @ValueSource(strings = {"강릉", "광주(유·스퀘어)", "부산_영도", "서울고속버스터미널(경부)"})
    void 한글_지점명으로도_URI_를_만든다(String keyword) {
        // build(true) 는 "이미 인코딩됨" 이라 원문 한글이 들어가면 Invalid character 로 던진다.
        assertDoesNotThrow(() -> GalleryPhotoClientImpl.searchUri(ENCODED_KEY, keyword, 5));
    }

    @Test
    void 키워드를_퍼센트_인코딩해_싣는다() {
        URI uri = GalleryPhotoClientImpl.searchUri(ENCODED_KEY, "강릉", 5);

        assertTrue(uri.toString().contains("keyword=%EA%B0%95%EB%A6%89"),
                "키워드가 인코딩되지 않았다: " + uri);
    }

    @Test
    void 서비스키는_다시_인코딩하지_않는다() {
        // 한 번 더 인코딩되면 %2B 가 %252B 가 되어 "등록되지 않은 서비스키" 가 된다(#165).
        URI uri = GalleryPhotoClientImpl.searchUri(ENCODED_KEY, "강릉", 5);

        assertTrue(uri.toString().contains("serviceKey=" + ENCODED_KEY),
                "서비스키가 다시 인코딩됐다: " + uri);
    }

    @Test
    void 디코딩하면_원래_말이_나온다() {
        URI uri = GalleryPhotoClientImpl.searchUri(ENCODED_KEY, "광주(유·스퀘어)", 5);

        // 인코딩만 하고 뜻을 바꾸지 않았는지 — 괄호·가운뎃점이 살아 있어야 그 지점을 찾는다.
        assertEquals("광주(유·스퀘어)",
                java.net.URLDecoder.decode(
                        uri.getRawQuery().replaceAll(".*keyword=([^&]*).*", "$1"),
                        java.nio.charset.StandardCharsets.UTF_8));
    }
}
