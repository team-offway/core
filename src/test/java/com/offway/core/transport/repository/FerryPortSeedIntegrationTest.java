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

    /** 시드된 항구 수. 마이그레이션이 잘리면 드러나게 정확한 값으로 고정한다. */
    private static final int EXPECTED_PORTS = 500;

    /**
     * 좌표를 가진 항구 — <b>302곳</b>(2026-09-06 재지오코딩 후).
     *
     * <p><b>387 에서 줄었다.</b> 시드는 이름만으로 좌표를 붙여 동음이의에 걸린 값이 섞여 있었다 —
     * `상노대`(통영 섬)가 철원 상노리에, `쑥섬`(전남 고흥)이 강화 두운리에 박혀 있었다. 항구 계열인지와
     * 이름·주소가 맞는지를 확인해 통과한 것만 남기니 128곳이 빠지고 43곳이 새로 채워졌다(#452).
     *
     * <p><b>줄어든 것이 손해가 아니다.</b> 틀린 좌표는 resolver 가 엉뚱한 곳을 답하게 한다 — 실제로
     * 철원군 코스의 대표 수단이 여객선으로 떴다. 빈 좌표는 최근접 탐색에서 빠질 뿐이다.
     */
    private static final int EXPECTED_WITH_COORDINATE = 302;

    /** 철원군청 — 내륙 최북단이다. 30㎞ 안에 항구가 있을 수 없다. */
    private static final double CHEORWON_LAT = 38.1466;
    private static final double CHEORWON_LNG = 127.3133;

    /** 완도군청 — 완도항이 1㎞ 안에 있다. */
    private static final double WANDO_LAT = 34.3110;
    private static final double WANDO_LNG = 126.7550;

    @Autowired
    private FerryPortRepository portRepository;

    @Autowired
    private FerryPortResolver resolver;

    @Test
    void 항구_마스터가_시드된다() {
        List<FerryPort> all = portRepository.findAll();

        assertEquals(EXPECTED_PORTS, all.size(), "항구 수가 다릅니다 — 마이그레이션이 잘렸는지 확인하세요");
        long withCoordinate = all.stream().filter(FerryPort::hasCoordinate).count();
        assertEquals(EXPECTED_WITH_COORDINATE, withCoordinate,
                "좌표 있는 항구 수가 다릅니다 — 시드·검증 규칙이 바뀌었는지 확인하세요");
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

    /**
     * 내륙 지역에는 항구가 없어야 한다(#452).
     *
     * <p>철원군은 <b>내륙 최북단</b>인데 코스의 대표 수단이 여객선으로 떴다. `상노대` 항구 좌표가
     * 철원군 동송읍 <b>상노리</b>를 가리키고 있었기 때문이다 — 실제 상노대도는 통영이고, 지오코더가
     * 앞 두 글자만 맞는 곳에 붙였다.
     *
     * <p>좌표 하나가 그 지역 전체의 안내를 바꾼다. 사용자는 배로 갈 수 없는 곳에 "배로 가세요" 를 듣고,
     * 서울에는 항구가 없어 <b>어디서 타는지도 못 듣는다.</b>
     */
    @Test
    void 내륙_지역에는_배로_닿지_않는다() {
        assertTrue(resolver.nearest(CHEORWON_LAT, CHEORWON_LNG).isEmpty(),
                "철원군 근처에 항구가 잡혔습니다 — 내륙인데 여객선이 대표 수단이 될 수 있습니다");
    }

    /**
     * 섬·해안 지역은 <b>제 항구</b>로 닿아야 한다(#452).
     *
     * <p>재지오코딩 전에는 완도가 `모황도`(작은 섬)로, 강화가 `쑥섬`(전남 고흥)으로 잡혔다. 이름을 못
     * 박으면 "어딘가 항구가 잡히긴 한다" 로 통과해, 정작 틀린 것을 놓친다.
     */
    @Test
    void 완도는_완도항으로_닿는다() {
        String nearest = resolver.nearest(WANDO_LAT, WANDO_LNG)
                .orElseThrow(() -> new AssertionError("완도군 근처에 항구가 없습니다"))
                .name();

        assertEquals("완도", nearest);
    }
}