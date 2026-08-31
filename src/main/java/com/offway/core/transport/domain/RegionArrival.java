package com.offway.core.transport.domain;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * 지역에 내리는 지점(#97) — 무엇을 타고 어디에 닿는가. 지역 안 동선의 기준점이 되는 값이다(#127).
 *
 * <p><b>왜 수단마다 따로 두지 않고 한 타입인가.</b> 코스가 알고 싶은 것은 "역이냐 터미널이냐" 가 아니라
 * <b>어디에 내리느냐</b> 하나다. 역({@link Station})·터미널({@link Terminal})·항구({@link Port}) 는 각자
 * 코드 체계가 다르지만 코스에 주는 값은 같아서, 여기서 한 모양으로 접는다.
 *
 * <p>후보가 여럿일 때 고르는 규칙도 이 타입이 소유한다({@link #nearestTo}). 서비스가 if 로 고르면 그 분기는
 * 테스트하기 어려운 자리에 남는다.
 *
 * @param mode 이 지점에 실어다 주는 수단
 * @param name 지점 이름(역명·터미널명·항구명)
 * @param point 지점 좌표 — 해석이 좌표 최근접이라 해석된 지점은 좌표를 반드시 가진다
 */
public record RegionArrival(TransitMode mode, String name, Coordinate point) {

    public RegionArrival {
        Objects.requireNonNull(mode, "수단은 null 일 수 없습니다.");
        Objects.requireNonNull(name, "지점 이름은 null 일 수 없습니다.");
        Objects.requireNonNull(point, "지점 좌표는 null 일 수 없습니다.");
    }

    /** 버스 터미널을 도착 지점으로 — 고속·시외 구분은 터미널이 이미 알고 있다. */
    public static RegionArrival of(Terminal terminal) {
        Objects.requireNonNull(terminal, "터미널은 null 일 수 없습니다.");
        return new RegionArrival(TransitMode.of(terminal.kind()), terminal.name(), terminal.coordinate());
    }

    /** 여객선 항구를 도착 지점으로. */
    public static RegionArrival of(Port port) {
        Objects.requireNonNull(port, "항구는 null 일 수 없습니다.");
        return new RegionArrival(TransitMode.FERRY, port.name(), port.coordinate());
    }

    /**
     * 후보 중 지역 좌표에 <b>가장 가까운</b> 지점. 후보가 하나도 없으면 빈 {@link Optional}.
     *
     * <p><b>왜 수단 우선순위가 아니라 거리인가.</b> 지역마다 어느 수단이 가까운지가 다르다 — 열차역이
     * 50㎞ 밖인데 시외버스 터미널이 읍내 한복판인 곳이 실제로 여럿이다(양양·합천·태안·진도·완도·함양 등).
     * 수단으로 순위를 매기면 그런 곳에서 40㎞ 떨어진 역을 동선 기준점으로 잡아, 지역 반대편부터 코스를 짠다.
     *
     * <p>{@code null} 후보는 건너뛴다 — 해석되지 않은 수단을 호출부가 걸러 넘기게 하면 같은 null 검사가
     * 부르는 쪽마다 생긴다.
     */
    public static Optional<RegionArrival> nearestTo(Coordinate region, RegionArrival... candidates) {
        Objects.requireNonNull(region, "지역 좌표는 null 일 수 없습니다.");
        if (candidates == null) {
            return Optional.empty();
        }
        return Arrays.stream(candidates)
                .filter(Objects::nonNull)
                .min(Comparator.comparingDouble(candidate -> region.haversineKmTo(candidate.point())));
    }
}
