package com.offway.core.transport.domain;

import java.util.Objects;

/**
 * 정류소에 곧 도착할 버스 한 대 — 노선번호·종류와 도착까지 남은 시간.
 *
 * <p>이 정보는 <b>실시간</b>이라 여행 계획 시점이 아니라 여행 당일에 쓴다. 다음 달 코스를 짜는 데는 쓸 수 없다.
 *
 * <p>분 단위는 컴포넌트로 두지 않고 초에서 계산하는 파생값({@link #arrivalMinutes()})으로 둔다 — 컴포넌트면 canonical
 * 생성자가 그대로 받아 초와 어긋난 분이 들어갈 수 있다.
 *
 * @param routeNo 노선번호(예: {@code 100}, {@code 농어촌2})
 * @param routeType 노선 종류(일반버스·좌석버스 등). 없을 수 있다
 * @param arrivalSeconds 도착까지 남은 초
 * @param stopsAway 남은 정류장 수
 */
public record BusArrival(String routeNo, String routeType, int arrivalSeconds, int stopsAway) {

    private static final int SECONDS_PER_MINUTE = 60;

    public BusArrival {
        Objects.requireNonNull(routeNo, "노선번호는 필수입니다");
        if (arrivalSeconds < 0) {
            throw new IllegalArgumentException("도착까지 남은 시간은 음수일 수 없습니다: " + arrivalSeconds);
        }
        if (stopsAway < 0) {
            throw new IllegalArgumentException("남은 정류장 수는 음수일 수 없습니다: " + stopsAway);
        }
    }

    /** 도착까지 남은 분 — 올림. 30초 남았는데 "0분" 이라고 하면 이미 지나간 것처럼 읽힌다. 나머지로 올림해 덧셈 오버플로를 피한다. */
    public int arrivalMinutes() {
        int minutes = arrivalSeconds / SECONDS_PER_MINUTE;
        return arrivalSeconds % SECONDS_PER_MINUTE == 0 ? minutes : minutes + 1;
    }
}
