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

    /**
     * <b>같은 계열이어도 서비스가 다르면 라벨이 달라야 한다</b>(#496).
     *
     * <p>셋 다 관광빅데이터 계열이지만 <b>활용신청이 각각 별개</b>고 한도도 따로다
     * (15101972 · 15128559 · 15128560). 라벨이 뭉치면 알림이 어느 서비스가 마른 건지 말하지 못한다.
     */
    @Test
    void 중심관광지는_tour_hub_다() {
        assertEquals(
                "tour-hub",
                ExternalSystems.label(
                        URI.create("https://apis.data.go.kr/B551011/LocgoHubTarService1/areaBasedList1?x=1")));
    }

    @Test
    void 연관관광지는_tour_related_다() {
        assertEquals(
                "tour-related",
                ExternalSystems.label(
                        URI.create("https://apis.data.go.kr/B551011/TarRlteTarService1/areaBasedList1?x=1")));
    }

    /** 축제 표준데이터는 host 도 다르다({@code api.} vs {@code apis.}) — 그래도 라벨이 있어야 한다. */
    @Test
    void 축제표준데이터는_festival_standard_다() {
        assertEquals(
                "festival-standard",
                ExternalSystems.label(
                        URI.create("https://api.data.go.kr/openapi/tn_pubr_public_cltur_fstvl_api?x=1")));
    }

    /**
     * <b>우리가 실제로 부르는 경로는 하나도 host 로 떨어지면 안 된다.</b>
     *
     * <p>매핑에서 빠지면 예외도 안 나고 조용히 host({@code apis.data.go.kr})가 라벨이 된다. 그러면
     * <b>서로 다른 API 의 실패가 한 칸에 뭉쳐</b> 한쪽 장애가 다른 쪽 성공에 묻힌다. 실제로 셋이
     * 그렇게 빠져 있었다 — 새 API 를 붙이고 여기 넣는 것을 잊는 것이 이 구멍이 생기는 방식이라,
     * 목록으로 전수 확인한다.
     */
    @Test
    void 우리가_부르는_경로는_host_로_떨어지지_않는다() {
        java.util.List<String> ours = java.util.List.of(
                "https://apis.data.go.kr/B551011/KorService2/areaBasedList2",
                "https://apis.data.go.kr/B551011/KorWithService2/detailWithTour2",
                "https://apis.data.go.kr/B551011/DataLabService/locgoRegnVisitrDDList",
                "https://apis.data.go.kr/B551011/LocgoHubTarService1/areaBasedList1",
                "https://apis.data.go.kr/B551011/TarRlteTarService1/areaBasedList1",
                "https://apis.data.go.kr/B551011/PhotoGalleryService1/galleryList1",
                "https://api.data.go.kr/openapi/tn_pubr_public_cltur_fstvl_api",
                "https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo",
                "https://apis.data.go.kr/1613000/ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList",
                "https://apis.data.go.kr/1613000/BusSttnInfoInqireService/getSttnNoList",
                "https://apis.data.go.kr/1360000/MidFcstInfoService/getMidLandFcst",
                "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst",
                "https://apis.openapi.sk.com/tmap/routes");

        for (String url : ours) {
            String label = ExternalSystems.label(URI.create(url));
            org.junit.jupiter.api.Assertions.assertFalse(
                    label.contains("data.go.kr") || label.contains("sk.com") || "unknown".equals(label),
                    "매핑에서 빠져 host 로 떨어졌다: " + url + " → " + label);
        }
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

    /**
     * 이 넷은 매핑이 비어 있어 전부 {@code apis.data.go.kr} 한 덩어리로 떨어지고 있었다(#474). 그러면
     * 고속버스만 죽었는지 게이트웨이 전체가 죽었는지를 못 가른다 — 장애 판정이 뭉개진다.
     */
    @Test
    void 나머지_TAGO_수단과_사진갤러리도_따로_갈린다() {
        assertEquals(
                "express-bus",
                ExternalSystems.label(
                        URI.create("https://apis.data.go.kr/1613000/ExpBusInfo/GetStrtpntAlocFndExpbusInfo")));
        assertEquals(
                "intercity-bus",
                ExternalSystems.label(
                        URI.create("https://apis.data.go.kr/1613000/SuburbsBusInfo/GetStrtpntAlocFndSuberbsBusInfo")));
        assertEquals(
                "ferry",
                ExternalSystems.label(
                        URI.create("https://apis.data.go.kr/1613000/DmstcShipNvgInfo/GetShipOpratInfoList")));
        assertEquals(
                "tour-photo",
                ExternalSystems.label(
                        URI.create("https://apis.data.go.kr/B551011/PhotoGalleryService1/gallerySearchList1")));
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
