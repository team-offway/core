package com.offway.core.trip.infrastructure.camping.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.trip.domain.CampingPlace;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 받은 야영장이 <b>코스에 올릴 수 있는 것인지</b> 가르는 판정(#510).
 *
 * <p>이 판정이 느슨하면 엔티티 불변식이 대신 예외를 던지는데, 그러면 <b>그달 적재가 통째로 실패한다</b> —
 * 한 건만 건너뛰면 될 일이다. 반대로 너무 빡빡하면 쓸 수 있는 야영장을 버린다.
 */
class GoCampsiteTest {

    private static final LocalDateTime FETCHED = LocalDateTime.of(2026, 9, 8, 4, 40);

    private static GoCampsite campsite(Double lat, Double lng, boolean operating) {
        return new GoCampsite("387", "국립남해편백자연휴양림", "경상남도 남해군 삼동면 금암로 658", "남해군",
                lat, lng, "일반야영장", "https://gocamping.or.kr/a.jpg",
                "편백나무 숲에서 캠핑", "긴 소개글", "055-867-7881", "https://www.foresttrip.go.kr/",
                "봄,여름,가을,겨울", "평일+주말", "온라인실시간예약", operating);
    }

    @Test
    void 운영중이고_좌표가_있으면_쓸_수_있다() {
        assertTrue(campsite(34.75, 128.02, true).isUsable());
    }

    /**
     * <b>휴장은 담지 않는다.</b> 실측 3,115건 중 123건이다. 문 닫은 야영장을 코스에 넣으면 여행자가
     * 헛걸음하는데, 그건 카드가 비는 것보다 나쁘다.
     */
    @Test
    void 휴장이면_쓸_수_없다() {
        assertFalse(campsite(34.75, 128.02, false).isUsable());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL | NULL",   // 좌표 없음 — 3,115건 중 10건
            "0.0 | 0.0",     // 출처가 안 채운 행
            "128.02 | 34.75", // 위경도를 바꿔 적음
            "41.0 | 128.0",  // 북한
    }, delimiter = '|', nullValues = "NULL")
    void 좌표를_못_쓰면_쓸_수_없다(Double lat, Double lng) {
        assertFalse(campsite(lat, lng, true).isUsable());
    }

    @Test
    void 이름이나_주소나_식별자가_없으면_쓸_수_없다() {
        GoCampsite base = campsite(34.75, 128.02, true);

        assertFalse(withName(base, null).isUsable());
        assertFalse(withName(base, "  ").isUsable());
        assertFalse(withAddress(base, null).isUsable());
        assertFalse(withExternalId(base, null).isUsable());
    }

    /**
     * 열여섯 칸이 제자리로 가는가.
     *
     * <p>{@link CampingPlace} 빌더가 필드명으로 받으므로 자리를 밀려 쓸 일은 없지만, <b>빠뜨린 칸</b>은
     * 컴파일이 안 잡는다 — 사진이나 한 줄 소개를 안 넘기면 이 소스를 들여온 이유가 사라진다.
     */
    @Test
    void 우리_도메인으로_빠짐없이_옮긴다() {
        CampingPlace place = campsite(34.75, 128.02, true).toPlace(7L, FETCHED);

        assertEquals(7L, place.getRegionId());
        assertEquals("387", place.getExternalId());
        assertEquals("국립남해편백자연휴양림", place.getName());
        assertEquals("경상남도 남해군 삼동면 금암로 658", place.getAddress());
        assertEquals(34.75, place.getLat());
        assertEquals(128.02, place.getLng());
        assertEquals("일반야영장", place.getInduty());
        assertEquals("https://gocamping.or.kr/a.jpg", place.getImageUrl());
        assertEquals("편백나무 숲에서 캠핑", place.getLineIntro());
        assertEquals("긴 소개글", place.getIntro());
        assertEquals("055-867-7881", place.getTel());
        assertEquals("https://www.foresttrip.go.kr/", place.getHomepageUrl());
        assertEquals("봄,여름,가을,겨울", place.getOperPeriod());
        assertEquals("평일+주말", place.getOperDays());
        assertEquals("온라인실시간예약", place.getReservation());
        assertEquals(FETCHED, place.getFetchedAt());
    }

    private static GoCampsite withName(GoCampsite base, String name) {
        return new GoCampsite(base.externalId(), name, base.address(), base.sigunguName(), base.lat(), base.lng(),
                base.induty(), base.imageUrl(), base.lineIntro(), base.intro(), base.tel(), base.homepageUrl(),
                base.operPeriod(), base.operDays(), base.reservation(), base.operating());
    }

    private static GoCampsite withAddress(GoCampsite base, String address) {
        return new GoCampsite(base.externalId(), base.name(), address, base.sigunguName(), base.lat(), base.lng(),
                base.induty(), base.imageUrl(), base.lineIntro(), base.intro(), base.tel(), base.homepageUrl(),
                base.operPeriod(), base.operDays(), base.reservation(), base.operating());
    }

    private static GoCampsite withExternalId(GoCampsite base, String externalId) {
        return new GoCampsite(externalId, base.name(), base.address(), base.sigunguName(), base.lat(), base.lng(),
                base.induty(), base.imageUrl(), base.lineIntro(), base.intro(), base.tel(), base.homepageUrl(),
                base.operPeriod(), base.operDays(), base.reservation(), base.operating());
    }
}
