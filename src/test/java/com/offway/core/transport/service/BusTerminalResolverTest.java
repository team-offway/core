package com.offway.core.transport.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.transport.domain.BusTerminal;
import com.offway.core.transport.domain.Terminal;
import com.offway.core.transport.repository.BusTerminalRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 좌표 → 최근접 터미널 해석(#107). 마스터는 stub 리포지토리로 격리한다.
 */
class BusTerminalResolverTest {

    /** 서울경부(강남)·태백·정선 고한사북. 좌표는 시드 실측값이다. */
    private static final List<BusTerminal> MASTER = List.of(
            BusTerminal.of("NAEK010", "서울경부", 37.5049, 127.0044),
            BusTerminal.of("NAEK274", "태백", 37.17698, 128.98524),
            BusTerminal.of("NAEK222", "고한사북", 37.2126, 128.8253),
            BusTerminal.of("NAEK999", "좌표없는곳", null, null));

    private static BusTerminalResolver resolver() {
        BusTerminalRepository repo = () -> MASTER; // findAll 단일 메서드 → 람다
        return new BusTerminalResolver(repo);
    }

    @Test
    void 가장_가까운_터미널을_찾는다() {
        // 태백 시청 근처 — 태백터미널이 코앞이다.
        Terminal terminal = resolver().nearest(37.1641, 128.9856).orElseThrow();

        assertEquals("NAEK274", terminal.code());
        assertEquals("태백", terminal.name());
    }

    @Test
    void 상한_거리_밖이면_빈_값이다() {
        // 제주 — 육지 터미널과 한참 멀다. "버스로 갈 수 있다" 고 잘못 안내하지 않는다.
        assertTrue(resolver().nearest(33.4996, 126.5312).isEmpty());
    }

    @Test
    void 좌표가_없는_터미널은_후보에서_빠진다() {
        // 목록에 실제 터미널이 아닌 항목이 섞여 있어 지오코딩이 전부 되지는 않는다.
        // 좌표 없는 행이 섞여도 최근접 계산이 터지지 않아야 한다.
        Terminal terminal = resolver().nearest(37.5049, 127.0044).orElseThrow();

        assertEquals("NAEK010", terminal.code());
    }

    @Test
    void 해석된_터미널은_좌표를_들고_있다() {
        // 코스는 집이 아니라 내린 터미널에서 시작한다(#127 이 열차에서 세운 규칙).
        Terminal terminal = resolver().nearest(37.2126, 128.8253).orElseThrow();

        assertEquals(37.2126, terminal.coordinate().lat(), 0.0001);
        assertEquals(128.8253, terminal.coordinate().lng(), 0.0001);
    }
}
