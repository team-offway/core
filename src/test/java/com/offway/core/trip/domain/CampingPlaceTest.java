package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 야영장 엔티티의 불변식과 행위(#510).
 *
 * <p>여기서 잠그는 것은 <b>후보 풀에 못 쓸 것이 들어오지 않는가</b> 다. 좌표가 없거나 범위 밖인 야영장은
 * 동선에 못 올리고, 그것이 코스 생성까지 흘러가면 거기서 터진다.
 */
class CampingPlaceTest {

    private static final LocalDateTime FETCHED = LocalDateTime.of(2026, 9, 8, 4, 40);

    private static CampingPlace.CampingPlaceBuilder valid() {
        return CampingPlace.builder()
                .regionId(1L)
                .externalId("387")
                .name("국립남해편백자연휴양림")
                .address("경상남도 남해군 삼동면 금암로 658")
                .lat(34.7521440529408)
                .lng(128.020135238357)
                .fetchedAt(FETCHED);
    }

    @Test
    void 받은_값이_제자리로_간다() {
        CampingPlace place = valid()
                .induty("일반야영장")
                .imageUrl("https://gocamping.or.kr/upload/camp/387/thumb.jpg")
                .lineIntro("편백나무 숲에서 피톤치드 맡으며 건강하게 캠핑을 하는 곳.")
                .intro("남해 편백자연휴양림은 ...")
                .tel("055-867-7881")
                .homepageUrl("https://www.foresttrip.go.kr/")
                .operPeriod("봄,여름,가을,겨울")
                .operDays("평일+주말")
                .reservation("온라인실시간예약")
                .build();

        assertEquals("387", place.getExternalId());
        assertEquals("국립남해편백자연휴양림", place.getName());
        assertEquals("일반야영장", place.getInduty());
        assertEquals("온라인실시간예약", place.getReservation());
        assertEquals(34.7521440529408, place.getLat());
        assertEquals(128.020135238357, place.getLng());
    }

    /** 좌표가 없으면 동선에 못 올린다 — 후보 풀에 들어오기 전에 막는다. */
    @ParameterizedTest
    @CsvSource({
            "0.0, 0.0",          // 출처가 안 채운 행
            "128.02, 34.75",     // 위경도를 바꿔 적은 행
            "41.0, 128.0",       // 북한
            "34.75, 140.0",      // 일본
    })
    void 대한민국_밖_좌표는_거절한다(double lat, double lng) {
        assertThrows(IllegalArgumentException.class, () -> valid().lat(lat).lng(lng).build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void 이름이_비면_거절한다(String name) {
        assertThrows(IllegalArgumentException.class, () -> valid().name(name).build());
    }

    @Test
    void 자연키가_없으면_거절한다() {
        // externalId 가 없으면 재적재마다 같은 야영장이 새 행으로 쌓인다.
        assertThrows(IllegalArgumentException.class, () -> valid().externalId(null).build());
    }

    @Test
    void 지역과_조회시각은_필수다() {
        assertThrows(NullPointerException.class, () -> valid().regionId(null).build());
        assertThrows(NullPointerException.class, () -> valid().fetchedAt(null).build());
    }

    /**
     * <b>사진 유무가 후보 순서를 가른다.</b> 이 표를 들여온 이유가 사진이라, 판정이 틀리면 사진 없는
     * 쪽이 앞에 와 얻는 것이 사라진다.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "https://gocamping.or.kr/a.jpg | true",
            "   | false",
            "NULL | false",
    }, delimiter = '|', nullValues = "NULL")
    void 사진이_있는지_스스로_안다(String imageUrl, boolean expected) {
        assertEquals(expected, valid().imageUrl(imageUrl).build().hasPhoto());
    }

    /** 운영 정보는 셋 중 하나만 있어도 상세에 실을 값이 있는 것이다. */
    @Test
    void 운영_정보를_하나라도_알면_안다고_답한다() {
        assertTrue(valid().operPeriod("연중").build().knowsOperation());
        assertTrue(valid().operDays("평일+주말").build().knowsOperation());
        assertTrue(valid().reservation("전화").build().knowsOperation());
        assertFalse(valid().build().knowsOperation());
    }

    /** 선택 값은 <b>길다고 버리지 않고 잘라 담는다</b> — 그 한 칸 때문에 이름·좌표까지 잃으면 안 된다. */
    @Test
    void 너무_긴_선택값은_잘라_담는다() {
        String tooLong = "가".repeat(600);

        CampingPlace place = valid().lineIntro(tooLong).build();

        assertEquals(500, place.getLineIntro().length());
    }

    @Test
    void 빈_선택값은_null_이다() {
        assertNull(valid().tel("  ").build().getTel());
        assertNull(valid().induty("").build().getInduty());
    }

    /** 필수 값은 반대로 <b>길면 거절한다</b> — 잘라 담으면 다른 야영장과 이름이 같아질 수 있다. */
    @Test
    void 너무_긴_이름은_거절한다() {
        assertThrows(IllegalArgumentException.class, () -> valid().name("가".repeat(201)).build());
    }

    /**
     * 접두어가 <b>다른 출처와 안 섞이는가</b>.
     *
     * <p>코스 응답에는 다섯 출처의 식별자가 함께 나간다. 이 판정 하나로 상세 조회가 어느 저장소를
     * 볼지 갈리므로, 남의 접두어를 우리 것으로 읽으면 엉뚱한 장소가 뜬다.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "CMP-42 | 42",
            "LIC-42 | NULL",
            "HER-42 | NULL",
            "FST-42 | NULL",
            "1234567 | NULL",
            "CMP-abc | NULL",
            "CMP- | NULL",
            "NULL | NULL",
    }, delimiter = '|', nullValues = "NULL")
    void 우리_식별자만_되돌린다(String publicId, Long expected) {
        assertEquals(Optional.ofNullable(expected), CampingPlace.parsePublicId(publicId));
    }

    @Test
    void 공개_식별자는_접두어를_붙인다() {
        assertEquals("CMP-42", CampingPlace.publicId(42L));
    }
}
