package com.offway.core.transport.infrastructure.kakao.dto;

import com.offway.core.common.geo.Coordinate;
import java.util.Objects;

/**
 * 외부 검색이 찾은 주소·장소 하나(#590).
 *
 * @param name 사용자에게 보일 이름 — 장소명이 있으면 그것, 없으면 주소
 * @param address 부제목에 쓸 주소. 이름이 이미 주소면 같은 값일 수 있다
 * @param coordinate 그 지점의 좌표 — 최근접 허브를 찾는 데 쓴다
 */
public record FoundPlace(String name, String address, Coordinate coordinate) {

    public FoundPlace {
        Objects.requireNonNull(name, "장소 이름은 필수입니다");
        Objects.requireNonNull(address, "주소는 필수입니다(없으면 빈 문자열)");
        Objects.requireNonNull(coordinate, "좌표는 필수입니다");
    }
}
