package com.offway.core.transport.domain;

import java.util.Objects;

/**
 * 버스 정류소 한 곳 — TAGO 정류소 코드와 이름·좌표.
 *
 * <p>{@code cityCode} 를 함께 담는 이유: TAGO 도착정보 조회가 {@code nodeId} 만으로는 안 되고 도시코드를 함께 요구한다.
 * 근접 정류소 조회 응답이 둘 다 주므로, 여기서 묶어두면 호출자가 도시코드를 따로 알아낼 필요가 없다.
 *
 * @param nodeId 정류소 코드(TAGO nodeid, 예: {@code GMB165})
 * @param name 정류소명
 * @param cityCode 도시코드(도착정보 조회에 함께 필요)
 * @param lat 위도
 * @param lng 경도
 */
public record BusStop(String nodeId, String name, int cityCode, double lat, double lng) {

    public BusStop {
        Objects.requireNonNull(nodeId, "정류소 코드는 필수입니다");
        Objects.requireNonNull(name, "정류소명은 필수입니다");
        if (nodeId.isBlank()) {
            throw new IllegalArgumentException("정류소 코드는 비어 있을 수 없습니다");
        }
    }
}
