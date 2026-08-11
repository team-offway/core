package com.offway.core.region.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.region.domain.Region;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 지역 마스터(#102) — 부팅 때 만들어 둔 것이 요청 경로에서 그대로 쓰이는가.
 *
 * <p>여기서 지키는 것은 <b>"요청마다 다시 계산하지 않는다"</b> 다. 89곳 좌표는 마이그레이션으로만 바뀌는데
 * 예전에는 추천·홈 요청마다 DB 를 읽고, 볼거리가 부족한 후보마다 haversine 89회를 다시 돌렸다.
 */
@SpringBootTest
class RegionMasterIntegrationTest {

    /** 시드에 담긴 인구감소지역 수. 이 값이 바뀌면 고시가 바뀐 것이라 테스트가 알려야 한다. */
    private static final int SEEDED_REGIONS = 89;

    @Autowired
    private RegionMaster regionMaster;

    @Test
    void 부팅하면_전체_지역을_들고_있다() {
        assertEquals(SEEDED_REGIONS, regionMaster.all().size());
    }

    @Test
    void 같은_리스트를_다시_만들지_않는다() {
        // 요청마다 DB 를 읽으면 매번 다른 인스턴스가 나온다. 같은 참조라는 것이 "다시 안 읽었다" 는 증거다.
        assertSame(regionMaster.all(), regionMaster.all());
    }

    @Test
    void 이웃은_가까운_순으로_정렬돼_있다() {
        long anyRegion = regionMaster.all().getFirst().getId();

        List<RegionMaster.Neighbor> neighbors = regionMaster.neighborsOf(anyRegion);

        assertFalse(neighbors.isEmpty());
        for (int i = 1; i < neighbors.size(); i++) {
            assertTrue(neighbors.get(i - 1).distanceKm() <= neighbors.get(i).distanceKm(),
                    "이웃이 거리 순이 아니다 — 쓰는 쪽이 반경으로 자를 때 뒤를 건너뛸 수 없다");
        }
    }

    @Test
    void 자기_자신은_이웃에_없다() {
        Region region = regionMaster.all().getFirst();

        assertTrue(regionMaster.neighborsOf(region.getId()).stream()
                .noneMatch(neighbor -> neighbor.region().getId().equals(region.getId())));
    }

    @Test
    void 반경과_개수로_잘라_준다() {
        long anyRegion = regionMaster.all().getFirst().getId();

        List<Region> within = regionMaster.neighborsWithin(anyRegion, 50.0, 3);

        // assertEquals 로 둔다 — 어긋났을 때 어떤 지역 ID 가 어떻게 갈렸는지가 실패 메시지에 그대로 남는다.
        List<Long> expected = regionMaster.neighborsOf(anyRegion).stream()
                .filter(neighbor -> neighbor.distanceKm() <= 50.0)
                .limit(3)
                .map(neighbor -> neighbor.region().getId())
                .toList();
        assertEquals(expected, within.stream().map(Region::getId).toList());
    }

    @Test
    void 없는_지역이면_빈_이웃이다() {
        assertTrue(regionMaster.neighborsOf(999_999L).isEmpty());
    }

    @Test
    void 모든_지역이_인접그래프를_갖는다() {
        // 한 곳이라도 빠지면 그 지역의 콘텐츠 보강이 조용히 멈춘다.
        assertTrue(regionMaster.all().stream()
                .allMatch(region -> !regionMaster.neighborsOf(region.getId()).isEmpty()));
    }
}
