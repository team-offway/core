package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LicensedPlaceTest {

    private static LicensedPlace.LicensedPlaceBuilder valid() {
        return LicensedPlace.builder()
                .regionId(16L)
                .kind(PlaceKind.STAY)
                .category(PlaceCategory.LODGING)
                .name("올인모텔")
                .address("경상북도 의성군 의성읍 북부길 5-4")
                .tel("0548341089")
                .lat(36.3527)
                .lng(128.6971);
    }

    @Test
    void 유효한_장소는_그대로_만들어진다() {
        LicensedPlace place = valid().build();

        assertEquals(16L, place.getRegionId());
        assertEquals(PlaceKind.STAY, place.getKind());
        assertEquals(PlaceCategory.LODGING, place.getCategory());
        assertEquals("올인모텔", place.getName());
        assertEquals("0548341089", place.getTel());
    }

    @Test
    void 지역이_없으면_거부한다() {
        assertThrows(NullPointerException.class, () -> valid().regionId(null).build());
    }

    @Test
    void 종류가_없으면_거부한다() {
        assertThrows(NullPointerException.class, () -> valid().kind(null).build());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 상호가_비면_거부한다(String blank) {
        assertThrows(IllegalArgumentException.class, () -> valid().name(blank).build());
    }

    /** 좌표 변환(EPSG:5174 → WGS84)이 어긋난 행이 그대로 실리면 코스가 엉뚱한 곳을 가리킨다. */
    @ParameterizedTest
    @CsvSource({"32.9, 128.0", "39.1, 128.0", "36.0, 123.9", "36.0, 132.1", "0, 0"})
    void 한국_밖_좌표는_거부한다(double lat, double lng) {
        assertThrows(IllegalArgumentException.class, () -> valid().lat(lat).lng(lng).build());
    }

    /** 전화번호는 인허가 데이터에서 29% 만 채워진다 — 없는 게 정상이라 빈 값은 null 로 눕힌다. */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void 전화번호가_비면_null_로_눕힌다(String blank) {
        assertNull(valid().tel(blank).build().getTel());
    }

    @Test
    void 상호와_주소의_앞뒤_공백은_다듬는다() {
        LicensedPlace place = valid().name("  올인모텔 ").address(" 의성읍 북부길 5-4  ").build();

        assertEquals("올인모텔", place.getName());
        assertEquals("의성읍 북부길 5-4", place.getAddress());
    }

    @Test
    void 좌표를_값객체로_내준다() {
        LicensedPlace place = valid().lat(36.3527).lng(128.6971).build();

        assertEquals(36.3527, place.coordinate().lat());
        assertEquals(128.6971, place.coordinate().lng());
    }

    // ── 공개 식별자(#144) ─────────────────────────────────────────

    @Test
    void 공개_식별자는_접두어를_붙인다() {
        assertEquals("LIC-42", LicensedPlace.publicId(42L));
    }

    @Test
    void 공개_식별자를_다시_숫자로_되돌린다() {
        assertEquals(java.util.Optional.of(42L), LicensedPlace.parsePublicId("LIC-42"));
    }

    /** TourAPI contentId 는 숫자 문자열이라 접두어가 없다. 남의 식별자를 우리 것으로 착각하면 안 된다. */
    @ParameterizedTest
    @ValueSource(strings = {"126508", "LIC-", "LIC-abc", "lic-42", "LIC-4 2", "", "  "})
    void 우리_식별자가_아니면_비어_있다(String other) {
        assertEquals(java.util.Optional.empty(), LicensedPlace.parsePublicId(other));
    }

    @Test
    void null_식별자도_비어_있다() {
        assertEquals(java.util.Optional.empty(), LicensedPlace.parsePublicId(null));
    }
}
