package com.offway.core.transport.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.geo.Coordinate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 앱이 되돌려 보내는 출발지 코드의 문법(#590). */
class OriginCodeTest {

    @Test
    void 기차역_코드는_접두로_종류를_알린다() {
        OriginCode code = OriginCode.ofHub(OriginHubType.TRAIN_STATION, "NAT010000");

        assertEquals("TRAIN:NAT010000", code.value());
        assertEquals(Optional.of(OriginHubType.TRAIN_STATION), code.hubType());
        assertEquals("NAT010000", code.hubCode());
        assertFalse(code.isCoordinate());
    }

    @Test
    void 버스_코드도_같다() {
        OriginCode code = OriginCode.ofHub(OriginHubType.BUS_TERMINAL, "NAEK030");

        assertEquals("BUS:NAEK030", code.value());
        assertEquals("NAEK030", code.hubCode());
    }

    @Test
    void 좌표_코드는_좌표를_품는다() {
        OriginCode code = OriginCode.ofCoordinate(new Coordinate(37.5665, 126.978));

        assertTrue(code.isCoordinate());
        assertTrue(code.hubType().isEmpty());
        assertEquals(Optional.of(new Coordinate(37.5665, 126.978)), code.coordinate());
    }

    @Test
    void 허브_코드에서_좌표를_꺼내려_하면_비어_있다() {
        assertTrue(OriginCode.ofHub(OriginHubType.TRAIN_STATION, "NAT010000").coordinate().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "GEO:", // 값이 없다
        "GEO:37.5665", // 한쪽만 있다
        "GEO:37.5665,126.978,99", // 칸이 셋이다
        "GEO:서울,중구", // 숫자가 아니다
        "GEO:91.0,126.978", // 위도 범위를 벗어났다
        "GEO:37.5665,181.0", // 경도 범위를 벗어났다
    })
    void 망가진_좌표_코드는_비어_있다(String raw) {
        // 우리가 발급한 값을 그대로 되돌려 받는 자리라, 망가졌다는 것은 앱이 손댔다는 뜻이다.
        // 여기서는 부재만 알리고 상태·메시지는 부르는 쪽이 정한다.
        assertTrue(new OriginCode(raw).coordinate().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"NAT010000", "TRAIN", "SUBWAY:NAT010000", ""})
    void 알_수_없는_접두는_허브도_좌표도_아니다(String raw) {
        OriginCode code = new OriginCode(raw);

        assertTrue(code.hubType().isEmpty());
        assertFalse(code.isCoordinate());
        assertTrue(code.coordinate().isEmpty());
    }
}
