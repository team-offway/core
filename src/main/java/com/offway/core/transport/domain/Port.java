package com.offway.core.transport.domain;

import java.util.Objects;

/**
 * 여객선 항구 한 곳(#97) — TAGO 항구 코드·이름과 좌표. 좌표를 항구로 해석할 때 쓴다.
 *
 * <p>기차역({@link Station})·버스터미널({@link Terminal})과 같은 모양이다. 코스는 집이 아니라 <b>내린 곳에서</b>
 * 시작하므로 좌표를 함께 든다(#127 이 열차에서 세운 규칙).
 *
 * @param code TAGO 항구 코드(예: {@code SEA43113})
 * @param name 항구 이름(예: 울릉_도동)
 * @param coordinate 항구 좌표 — 해석이 좌표 최근접이라 해석된 항구는 좌표를 반드시 가진다
 */
public record Port(String code, String name, Coordinate coordinate) {

    public Port {
        Objects.requireNonNull(code, "항구 코드는 null 일 수 없습니다.");
        Objects.requireNonNull(name, "항구 이름은 null 일 수 없습니다.");
        Objects.requireNonNull(coordinate, "항구 좌표는 null 일 수 없습니다.");
    }
}
