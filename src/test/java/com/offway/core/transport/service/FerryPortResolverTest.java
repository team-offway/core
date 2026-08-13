package com.offway.core.transport.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.transport.domain.FerryPort;
import com.offway.core.transport.domain.Port;
import com.offway.core.transport.repository.FerryPortRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 좌표 → 최근접 항구 해석(#97). 마스터는 stub 리포지토리로 격리한다.
 */
class FerryPortResolverTest {

    /** 울릉 3항·포항·묵호. 좌표는 시드 실측값이다. */
    private static final List<FerryPort> MASTER = List.of(
            FerryPort.of("SEA43113", "울릉_도동", 37.48234555, 130.89656084),
            FerryPort.of("SEA43111", "울릉_저동", 37.49273354, 130.91019818),
            FerryPort.of("SEA43010", "포항", 36.04170109, 129.36710106),
            FerryPort.of("SEA44030", "묵호", 37.55232928, 129.11641147),
            FerryPort.of("SEA43110", "울릉도", null, null));

    private static FerryPortResolver resolver() {
        FerryPortRepository repo = () -> MASTER; // findAll 단일 메서드 → 람다
        return new FerryPortResolver(repo);
    }

    @Test
    void 울릉군청에서_도동항이_잡힌다() {
        // 울릉군은 인구감소지역 89곳 중 버스로 못 닿는 유일한 곳이다. 배가 유일한 수단이라 여기가 핵심이다.
        Port port = resolver().nearest(37.4843177, 130.9055044).orElseThrow();

        assertEquals("SEA43113", port.code());
        assertEquals("울릉_도동", port.name());
    }

    @Test
    void 내륙_한복판은_배로_갈_수_없다() {
        // 상한을 넓게 잡으면 육지 좌표에 "배로 갈 수 있다" 고 답하게 된다. 대전 시청 좌표로 확인한다.
        assertTrue(resolver().nearest(36.3504, 127.3845).isEmpty());
    }

    @Test
    void 좌표가_없는_항구는_후보에서_빠진다() {
        // 짧은 섬 이름이 상호명과 충돌해 지오코딩이 엉뚱한 곳을 잡은 항구는 좌표를 비워 뒀다.
        // 그런 행이 섞여도 최근접 계산이 터지지 않아야 한다.
        Port port = resolver().nearest(36.04170109, 129.36710106).orElseThrow();

        assertEquals("포항", port.name());
    }

    @Test
    void 해석된_항구는_좌표를_들고_있다() {
        // 코스는 집이 아니라 내린 곳에서 시작한다(#127 이 열차에서 세운 규칙).
        Port port = resolver().nearest(37.55232928, 129.11641147).orElseThrow();

        assertEquals(37.55232928, port.coordinate().lat(), 0.0001);
    }
}
