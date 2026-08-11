package com.offway.core.common.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import org.junit.jupiter.api.Test;

class ExternalSystemsTest {

    @Test
    void 관광정보는_tour_다() {
        assertEquals(
                "tour",
                ExternalSystems.label(URI.create("https://apis.data.go.kr/B551011/KorService2/areaBasedList2?x=1")));
    }

    @Test
    void 무장애관광은_tour_with_다() {
        assertEquals(
                "tour-with",
                ExternalSystems.label(URI.create("https://apis.data.go.kr/B551011/KorWithService2/detailWithTour2")));
    }

    @Test
    void 관광빅데이터는_datalab_이다() {
        assertEquals(
                "datalab",
                ExternalSystems.label(
                        URI.create("https://apis.data.go.kr/B551011/DataLabService/locgoRegnVisitrDDList")));
    }

    @Test
    void 특일정보는_holiday_다() {
        assertEquals(
                "holiday",
                ExternalSystems.label(
                        URI.create("https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo")));
    }

    @Test
    void tmap_은_tmap_이다() {
        assertEquals("tmap", ExternalSystems.label(URI.create("https://apis.openapi.sk.com/tmap/routes?version=1")));
    }

    @Test
    void 매핑에_없는_주소는_호스트로_떨어진다() {
        assertEquals("example.com", ExternalSystems.label(URI.create("https://example.com/some/path")));
    }

    @Test
    void 호스트도_없으면_unknown_이다() {
        assertEquals("unknown", ExternalSystems.label(URI.create("/relative/path")));
    }
}
