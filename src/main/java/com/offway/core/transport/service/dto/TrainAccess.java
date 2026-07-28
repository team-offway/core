package com.offway.core.transport.service.dto;

import com.offway.core.transport.infrastructure.tago.dto.TrainLeg;

/**
 * 지역까지의 열차 접근 결과 — transport 가 itinerary(코스)에 주는 값. 네 상태를 구분해 UI 가 정확히 안내하게 한다.
 *
 * @param status 접근 상태
 * @param fromStation 출발역명(없으면 null)
 * @param toStation 도착역명(없으면 null)
 * @param fastest 가장 빠른 열차편(AVAILABLE 일 때만, 아니면 null)
 */
public record TrainAccess(Status status, String fromStation, String toStation, TrainLeg fastest) {

    public enum Status {
        /** 운행 열차 있음. */
        AVAILABLE,
        /** 출발지·지역에 기차역이 없어 열차로 갈 수 없음. */
        NO_STATION,
        /** 역은 있으나 그 날짜에 운행이 없음. */
        NO_SERVICE_ON_DATE,
        /** 조회 실패(키 없음·외부 오류). */
        UNAVAILABLE
    }

    public static TrainAccess available(String fromStation, String toStation, TrainLeg fastest) {
        return new TrainAccess(Status.AVAILABLE, fromStation, toStation, fastest);
    }

    public static TrainAccess noStation(String fromStation, String toStation) {
        return new TrainAccess(Status.NO_STATION, fromStation, toStation, null);
    }

    public static TrainAccess noServiceOnDate(String fromStation, String toStation) {
        return new TrainAccess(Status.NO_SERVICE_ON_DATE, fromStation, toStation, null);
    }

    /** 역은 해석됐으나 조회가 실패한 경우 — 해석된 역명은 그대로 담는다(해석과 조회는 별개). */
    public static TrainAccess unavailable(String fromStation, String toStation) {
        return new TrainAccess(Status.UNAVAILABLE, fromStation, toStation, null);
    }
}
