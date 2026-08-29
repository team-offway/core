package com.offway.core.transport.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CoordinateKeyTest {

    /**
     * 같은 장소를 두 경로로 읽어도 한 키여야 한다. 아니면 차단해 둔 좌표가 다음 코스에서 되살아난다 —
     * 이 클래스가 있는 이유가 그것이다.
     */
    @Test
    void 같은_좌표는_어느_경로로_만들어도_같은_키다() {
        assertEquals(CoordinateKey.of(37.9419179, 127.3836649),
                CoordinateKey.of(new Coordinate(37.9419179, 127.3836649)));
    }

    /**
     * 차단 목록은 {@code Set} 으로 오간다. {@code BigDecimal} 은 자릿수가 다르면 {@code equals} 가 거짓이라
     * ({@code 37.5} ≠ {@code 37.5000000}) 여기서 갈리면 조회가 조용히 아무것도 못 찾는다.
     */
    @Test
    void 집합에서_같은_것으로_묶인다() {
        Set<CoordinateKey> keys =
                new HashSet<>(List.of(CoordinateKey.of(37.5, 127.5), CoordinateKey.of(37.50, 127.50)));

        assertEquals(1, keys.size());
    }

    /** 소수 7자리는 약 1cm 다 — 우리 좌표 출처가 주는 정밀도보다 촘촘해 서로 다른 장소가 합쳐지지 않는다. */
    @Test
    void 저장_자릿수보다_아래는_반올림해_한_키가_된다() {
        assertEquals(CoordinateKey.of(37.94191794, 127.3836649),
                CoordinateKey.of(37.94191791, 127.3836649));
    }

    @Test
    void 저장_자릿수_안에서_다르면_다른_키다() {
        assertNotEquals(CoordinateKey.of(37.9419179, 127.3836649),
                CoordinateKey.of(37.9419178, 127.3836649));
    }

    /** DB 컬럼이 {@code DECIMAL(10,7)} 이라 자릿수가 어긋나면 저장값과 조회 키가 안 맞는다. */
    @Test
    void 자릿수가_DB_컬럼과_같다() {
        assertEquals(CoordinateKey.SCALE, CoordinateKey.of(37.5, 127.5).lat().scale());
        assertEquals(CoordinateKey.SCALE, CoordinateKey.of(37.5, 127.5).lng().scale());
    }

    @Test
    void 음수_좌표도_다룬다() {
        assertEquals(CoordinateKey.of(-33.8688, 151.2093), CoordinateKey.of(-33.8688, 151.2093));
    }
}
