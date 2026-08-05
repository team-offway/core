package com.offway.core.transport.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.transport.domain.FerryPort;
import com.offway.core.transport.service.FerryPortResolver;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 여객선 항구 시드 검증(#97) — 마이그레이션이 실제로 적용되고 울릉 접근이 되는지 본다.
 *
 * <p>울릉군은 인구감소지역 89곳 중 <b>버스로 못 닿는 유일한 곳</b>이다. 이 시드가 깨지면 그 지역만 접근 정보가
 * 통째로 사라지는데, 부가 정보라 화면에서는 티가 안 난다.
 */
@SpringBootTest
class FerryPortSeedIntegrationTest {

    /** TAGO 항구 목록 실측(2026-08-05) 500곳. */
    private static final int EXPECTED_PORTS = 500;

    /**
     * 지오코딩으로 좌표를 확보한 행 수(2026-08-05 기준 388).
     *
     * <p>짧은 섬 이름이 상호명과 충돌해 엉뚱한 곳이 잡힌 것은 <b>좌표를 비웠다</b> — 틀린 좌표를 남기면 육지
     * 한복판에서 "배로 갈 수 있다" 고 답하게 된다. 하한만 본다(다시 돌려 늘어나는 것은 정상).
     */
    private static final int MIN_WITH_COORDINATE = 370;

    @Autowired
    private FerryPortRepository portRepository;

    @Autowired
    private FerryPortResolver resolver;

    @Test
    void 항구_마스터가_시드된다() {
        List<FerryPort> all = portRepository.findAll();

        assertEquals(EXPECTED_PORTS, all.size(), "항구 수가 다릅니다 — 마이그레이션이 잘렸는지 확인하세요");
        long withCoordinate = all.stream().filter(FerryPort::hasCoordinate).count();
        assertTrue(withCoordinate >= MIN_WITH_COORDINATE,
                "좌표 있는 항구가 " + withCoordinate + "곳뿐입니다 — 최근접 탐색이 그만큼 좁아집니다");
    }

    @Test
    void 항구_코드는_중복되지_않는다() {
        List<FerryPort> all = portRepository.findAll();

        assertEquals(all.size(), all.stream().map(FerryPort::getCode).distinct().count(),
                "항구 코드가 중복됩니다 — 운항정보 조회가 엉뚱한 곳을 가리킵니다");
    }

    @Test
    void 울릉군이_배로_닿는다() {
        // 버스가 못 가는 유일한 지역. 이 테스트가 깨지면 89곳 중 한 곳이 통째로 빈다.
        var port = resolver.nearest(37.4843177, 130.9055044).orElseThrow(
                () -> new AssertionError("울릉군에서 항구를 못 찾았습니다 — 시드·지오코딩 회귀"));

        assertTrue(port.name().startsWith("울릉"), "울릉 밖 항구가 잡혔습니다: " + port.name());
    }

    @Test
    void 울릉으로_가는_출발항이_시드에_있다() {
        // 포항·묵호가 있어야 "육지에서 울릉까지" 를 답할 수 있다.
        List<String> names = portRepository.findAll().stream().map(FerryPort::getName).toList();

        assertTrue(names.contains("포항"), "포항항이 없습니다");
        assertTrue(names.contains("묵호"), "묵호항이 없습니다");
    }
}
