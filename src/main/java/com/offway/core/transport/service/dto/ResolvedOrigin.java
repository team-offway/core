package com.offway.core.transport.service.dto;

import com.offway.core.common.geo.Coordinate;
import java.util.Objects;

/**
 * 출발지 코드를 풀어낸 결과 — 좌표와 이름(#590).
 *
 * <p>코스 생성·추천이 쓰던 {@code originLat}·{@code originLng} 자리에 이 값이 들어간다. 이름은 카드의
 * "서울에서 출발" 을 그리는 값이라(#382), 앱이 {@code fromPlace} 를 따로 실어 보내지 않아도 되게 한다.
 *
 * @param coordinate 동선 정렬·최근접 허브 계산이 쓰는 좌표
 * @param name 사람이 부르는 이름 — 허브면 표시 이름, 주소면 그 주소
 */
public record ResolvedOrigin(Coordinate coordinate, String name) {

    public ResolvedOrigin {
        Objects.requireNonNull(coordinate, "좌표는 필수입니다");
        Objects.requireNonNull(name, "출발지 이름은 필수입니다");
    }
}
