package com.offway.core.transport.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 좌표를 <b>같음 비교가 되는 형태</b>로 고정한 키(#335).
 *
 * <p>{@code double} 을 그대로 키로 쓰면 안 된다. 같은 장소를 두 경로로 읽어 마지막 자리가 한 번이라도
 * 달라지면 다른 좌표가 되어, 차단해 둔 것이 다음 코스에서 되살아난다. DB 컬럼도 같은 {@code DECIMAL(10,7)}
 * 이라 저장·조회가 한 규격을 쓴다.
 *
 * <p>소수 7자리는 약 1cm 다 — 우리 좌표 출처(인허가·TourAPI)가 주는 정밀도보다 이미 촘촘해, 반올림으로
 * 서로 다른 장소가 한 키로 합쳐질 일은 없다.
 */
public record CoordinateKey(BigDecimal lat, BigDecimal lng) {

    /** DB {@code DECIMAL(10,7)} 과 같은 자릿수. 한쪽만 바꾸면 저장값과 조회 키가 어긋난다. */
    public static final int SCALE = 7;

    public static CoordinateKey of(double lat, double lng) {
        return new CoordinateKey(scaled(lat), scaled(lng));
    }

    public static CoordinateKey of(Coordinate coordinate) {
        return of(coordinate.lat(), coordinate.lng());
    }

    private static BigDecimal scaled(double value) {
        return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP);
    }
}
