package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 국가유산 엔티티(#160) — 이미지 URL 승격·좌표 검증·공개 식별자.
 *
 * <p>여기서 지키는 것은 <b>"카드에 사진이 나가는가"</b> 와 <b>"엉뚱한 자리에 서지 않는가"</b> 다.
 */
class HeritagePlaceTest {

    private static HeritagePlace.HeritagePlaceBuilder valid() {
        return HeritagePlace.builder()
                .regionId(1L)
                .kind("보물")
                .group(HeritageGroup.HISTORIC_STRUCTURE)
                .name("법화사 묘법연화경")
                .address("부산광역시 영도구 중복길 490")
                .lat(35.0896577)
                .lng(129.0489314);
    }

    @Test
    void http_이미지는_https_로_올린다() {
        // 원본이 주는 URL 은 http 인데 그대로 부르면 302 로 튕긴다(실측: http 302 text/html 140B,
        // https 200 image/jpg 69,774B). 그대로 저장하면 앱에서 한 장도 안 뜬다.
        HeritagePlace place = valid()
                .imageUrl("http://www.khs.go.kr/unisearch/images/treasure/1620596.jpg")
                .build();

        assertEquals("https://www.khs.go.kr/unisearch/images/treasure/1620596.jpg", place.getImageUrl());
    }

    @Test
    void 이미_https_면_그대로_둔다() {
        HeritagePlace place = valid().imageUrl("https://example.test/a.jpg").build();

        assertEquals("https://example.test/a.jpg", place.getImageUrl());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void 사진이_없으면_null_이다(String blank) {
        assertNull(valid().imageUrl(blank).build().getImageUrl());
    }

    @Test
    void 사진이_없어도_국가유산은_만들어진다() {
        // 6,387건 중 2% 는 사진이 없다. 사진 하나 때문에 이름·좌표·설명이 멀쩡한 유산을 버리지 않는다.
        HeritagePlace place = valid().imageUrl(null).description(null).build();

        assertNull(place.getImageUrl());
        assertNull(place.getDescription());
        assertEquals("법화사 묘법연화경", place.getName());
    }

    @ParameterizedTest
    @CsvSource({
            "32.9, 127.0",   // 위도 하한 밖
            "39.1, 127.0",   // 위도 상한 밖
            "36.0, 123.9",   // 경도 하한 밖
            "36.0, 132.1",   // 경도 상한 밖
    })
    void 대한민국_밖_좌표는_거부한다(double lat, double lng) {
        // 지오코딩이 엉뚱하게 찍은 행을 여기서 끊는다 — 좌표 하나가 틀리면 그 코스의 동선이 통째로 어긋난다.
        assertThrows(IllegalArgumentException.class, () -> valid().lat(lat).lng(lng).build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void 이름이_비면_거부한다(String blank) {
        assertThrows(IllegalArgumentException.class, () -> valid().name(blank).build());
    }

    @Test
    void 소재지가_비면_거부한다() {
        assertThrows(IllegalArgumentException.class, () -> valid().address(null).build());
    }

    @Test
    void 공개_식별자는_접두어로_출처를_구분한다() {
        // 코스 응답의 poiContentId 에는 TourAPI(숫자)·인허가(LIC-)·국가유산(HER-)이 섞여 나간다.
        assertEquals("HER-42", HeritagePlace.publicId(42L));
        assertEquals(Optional.of(42L), HeritagePlace.parsePublicId("HER-42"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"LIC-42", "12345", "HER-abc", "HER-"})
    void 우리_식별자가_아니면_비어_있음을_준다(String other) {
        assertTrue(HeritagePlace.parsePublicId(other).isEmpty());
    }

    @Test
    void 방문_가능_여부는_대분류가_정한다() {
        assertTrue(valid().group(HeritageGroup.HISTORIC_STRUCTURE).build().isVisitable());
        assertFalse(valid().group(HeritageGroup.ARTIFACT).build().isVisitable());
    }
}
