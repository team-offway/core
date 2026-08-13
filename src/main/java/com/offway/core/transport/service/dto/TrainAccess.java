package com.offway.core.transport.service.dto;

import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.domain.TrainLeg;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 지역까지의 열차 접근 결과 — transport 가 itinerary(코스)에 주는 값. 네 상태를 구분해 UI 가 정확히 안내하게 한다.
 *
 * <p><b>도착 지점과 도착 시각을 함께 준다.</b> 코스는 집이 아니라 <b>내린 역에서</b> 시작하고, 오후에 닿았으면 그날 오전
 * 일정은 지킬 수 없다. 이 둘이 없으면 itinerary 는 출발지 좌표와 1일차 오전이라는 <b>틀린 전제</b>로 코스를 짠다(#127).
 *
 * <p>둘의 <b>가용 조건이 다르다</b> — 도착 지점은 역이 해석되기만 하면 알 수 있고(그날 운행이 없어도, 조회가 실패해도),
 * 도착 시각은 실제 운행 편을 찾았을 때만 안다. 그래서 별도 필드로 두고 각각 {@link Optional} 로 답한다.
 *
 * @param status 접근 상태
 * @param fromStation 출발역명(없으면 null)
 * @param toStation 도착역명(없으면 null)
 * @param toPoint 도착역 좌표(역이 해석됐으면 non-null) — 지역 안 동선의 기준점
 * @param fastest 가장 빠른 열차편(AVAILABLE 일 때만, 아니면 null)
 */
public record TrainAccess(
        Status status, String fromStation, String toStation, Coordinate toPoint, TrainLeg fastest) {

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

    /**
     * 지역에 닿는 지점 — 코스 첫 장소를 고르는 기준점. 역이 해석되기만 하면 <b>운행·조회 결과와 무관하게</b> 답한다.
     *
     * <p>그날 운행이 없거나 조회가 실패해도, 그 지역에 열차로 간다면 내리는 곳은 그 역이다. 여기서 빈 값을 주면 호출자가
     * 출발지 좌표로 되돌아가 반대편 동선을 짠다.
     */
    public Optional<Coordinate> arrivalPoint() {
        return Optional.ofNullable(toPoint);
    }

    /** 지역에 닿는 시각 — 실제 운행 편을 찾았을 때만 안다. 1일차에 어느 시간대부터 일정을 넣을지의 근거다. */
    public Optional<LocalDateTime> arrivalAt() {
        return Optional.ofNullable(fastest).map(TrainLeg::arriveAt);
    }

    public static TrainAccess available(String fromStation, String toStation, Coordinate toPoint, TrainLeg fastest) {
        return new TrainAccess(Status.AVAILABLE, fromStation, toStation, toPoint, fastest);
    }

    /** 역 자체가 없어 열차로 못 가는 경우 — 도착 지점도 없다. */
    public static TrainAccess noStation(String fromStation, String toStation) {
        return new TrainAccess(Status.NO_STATION, fromStation, toStation, null, null);
    }

    /** 역은 해석됐으나 그날 운행이 없는 경우 — 시각은 모르지만 <b>지점은 안다</b>. */
    public static TrainAccess noServiceOnDate(String fromStation, String toStation, Coordinate toPoint) {
        return new TrainAccess(Status.NO_SERVICE_ON_DATE, fromStation, toStation, toPoint, null);
    }

    /** 역은 해석됐으나 조회가 실패한 경우 — 해석된 역명·좌표는 그대로 담는다(해석과 조회는 별개). */
    public static TrainAccess unavailable(String fromStation, String toStation, Coordinate toPoint) {
        return new TrainAccess(Status.UNAVAILABLE, fromStation, toStation, toPoint, null);
    }
}
