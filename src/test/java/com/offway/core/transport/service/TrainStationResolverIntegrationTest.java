package com.offway.core.transport.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.transport.domain.Station;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 역 마스터 시드(343역) + 좌표 최근접 해석 통합 검증 — 시드된 DB 로 외부 없이 확인. */
@SpringBootTest
class TrainStationResolverIntegrationTest {

    @Autowired
    private TrainStationResolver resolver;

    @Test
    void 좌표에서_가장_가까운_역을_찾는다() {
        // 경주 시내 좌표 → 경주역
        Optional<Station> station = resolver.nearest(35.7980, 129.1405);

        assertTrue(station.isPresent());
        assertEquals("경주", station.get().name());
    }

    @Test
    void 근교에_역이_없는_먼_좌표는_빈결과다() {
        // 제주 — 육지 역과 50km 밖이라 열차 접근 불가
        assertTrue(resolver.nearest(33.4996, 126.5312).isEmpty());
    }
}
