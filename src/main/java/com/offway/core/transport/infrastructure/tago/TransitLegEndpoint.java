package com.offway.core.transport.infrastructure.tago;

import com.offway.core.common.external.ExternalApi;
import com.offway.core.transport.domain.TransitMode;

/**
 * 수단별 TAGO 구간 조회 계약(#107 · #97) — base·오퍼레이션·파라미터 키가 서비스마다 다르다.
 *
 * <p>셋 다 <b>짧은 base + 대문자 G</b> 규칙을 따른다(실호출 확정 2026-08-31). 긴 base({@code ...Service})와
 * 소문자 조합은 {@code 400 NO_OPENAPI_SERVICE_ERROR} 다.
 *
 * <p>버스와 배는 파라미터 키가 갈린다 — 버스는 {@code depTerminalId}, 배는 {@code depNodeId} 다. 등급을 담는
 * 필드도 {@code gradeNm}(우등) 과 {@code vihicleNm}(선명, <b>오타가 아니라 실제 필드명</b>)으로 다르다.
 */
public enum TransitLegEndpoint {

    EXPRESS_BUS(
            "ExpBusInfo/GetStrtpntAlocFndExpbusInfo",
            "depTerminalId", "arrTerminalId", "gradeNm", ExternalApi.EXPRESS_BUS_INFO),

    INTERCITY_BUS(
            "SuburbsBusInfo/GetStrtpntAlocFndSuberbsBusInfo",
            "depTerminalId", "arrTerminalId", "gradeNm", ExternalApi.INTERCITY_BUS_INFO),

    FERRY(
            "DmstcShipNvgInfo/GetShipOpratInfoList",
            "depNodeId", "arrNodeId", "vihicleNm", ExternalApi.SHIP_INFO);

    private static final String BASE = "https://apis.data.go.kr/1613000/";

    private final String path;
    private final String depKey;
    private final String arrKey;
    private final String vehicleField;
    private final ExternalApi api;

    TransitLegEndpoint(String path, String depKey, String arrKey, String vehicleField, ExternalApi api) {
        this.path = path;
        this.depKey = depKey;
        this.arrKey = arrKey;
        this.vehicleField = vehicleField;
        this.api = api;
    }

    /**
     * 수단에 대응하는 계약. 열차와 자차는 여기 없다 — 열차는 {@link TrainInfoClient} 가 시각까지 답하는
     * 별도 경로고, 자차는 구간이라는 개념 자체가 없다(#379).
     *
     * @throws IllegalArgumentException 열차·자차를 넘긴 경우(불변식 — 호출부가 걸러야 한다)
     */
    public static TransitLegEndpoint of(TransitMode mode) {
        return switch (mode) {
            case EXPRESS_BUS -> EXPRESS_BUS;
            case INTERCITY_BUS -> INTERCITY_BUS;
            case FERRY -> FERRY;
            case TRAIN -> throw new IllegalArgumentException("열차는 TrainInfoClient 가 담당합니다.");
            case CAR -> throw new IllegalArgumentException("자차는 구간 조회 대상이 아닙니다.");
        };
    }

    String url() {
        return BASE + path;
    }

    String depKey() {
        return depKey;
    }

    String arrKey() {
        return arrKey;
    }

    String vehicleField() {
        return vehicleField;
    }

    /**
     * 이 엔드포인트가 태우는 한도의 주인.
     *
     * <p><b>같은 도메인 안에서 열어 둔다</b>(#414). 어느 수단이 어느 TAGO API 를 쓰는지는 여기가 유일한
     * 정본인데, 캐시 스위치(#403)가 API 별이라 서비스 계층도 그 매핑이 필요하다. 서비스가 자기 switch 를
     * 또 들면 API 가 늘거나 바뀔 때 두 곳이 조용히 갈린다.
     */
    public ExternalApi api() {
        return api;
    }
}
