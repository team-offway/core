package com.offway.core.transport.domain;

import java.util.Objects;

/**
 * 고속버스 터미널 한 곳(#107) — TAGO 터미널 코드·이름과 좌표. 좌표를 터미널로 해석할 때 쓴다.
 *
 * <p>기차역({@link Station})과 같은 모양이다. 코스는 집이 아니라 <b>내린 터미널에서</b> 시작하므로 좌표를 함께 든다
 * (#127 이 열차에서 세운 규칙을 버스도 그대로 따른다).
 *
 * @param code TAGO 터미널 코드(예: {@code NAEK010})
 * @param name 터미널 이름(예: 서울경부)
 * @param coordinate 터미널 좌표 — 해석이 좌표 최근접이라 해석된 터미널은 좌표를 반드시 가진다
 */
public record Terminal(String code, String name, Coordinate coordinate) {

    public Terminal {
        Objects.requireNonNull(code, "터미널 코드는 null 일 수 없습니다.");
        Objects.requireNonNull(name, "터미널 이름은 null 일 수 없습니다.");
        Objects.requireNonNull(coordinate, "터미널 좌표는 null 일 수 없습니다.");
    }
}
