package com.offway.core.transport.domain;

import com.offway.core.common.geo.Coordinate;
import java.util.Objects;
import lombok.Builder;

/**
 * 기차역 한 곳 — TAGO 역 코드·역명과 좌표. 지역(시군구명)·출발지를 역으로 해석할 때 쓴다.
 *
 * <p><b>좌표를 함께 든다.</b> 대중교통 코스는 집이 아니라 <b>내린 역에서</b> 시작하므로, 지역 안 첫 장소를 고르는 기준점이
 * 역 좌표다. 역명(String)만 넘기면 그 기준점을 잃어 출발지 좌표로 동선을 짜게 된다 — 서울→경주인데 "경주 장소들 중 서울에서
 * 가까운 곳" 부터 이어붙는다(#127).
 *
 * @param id 역 코드(TAGO nodeid, 예: {@code NAT010000})
 * @param name 역명(예: 서울·정선)
 * @param coordinate 역 좌표 — 해석 자체가 좌표 최근접이라 해석된 역은 좌표를 반드시 가진다
 */
@Builder
public record Station(String id, String name, Coordinate coordinate) {

    public Station {
        // 셋 다 불변식이다. 좌표 없는 역은 최근접 해석 대상이 아니라 애초에 여기 닿으면 안 된다.
        Objects.requireNonNull(id, "역 코드는 null 일 수 없습니다.");
        Objects.requireNonNull(name, "역명은 null 일 수 없습니다.");
        Objects.requireNonNull(coordinate, "역 좌표는 null 일 수 없습니다.");
    }
}
