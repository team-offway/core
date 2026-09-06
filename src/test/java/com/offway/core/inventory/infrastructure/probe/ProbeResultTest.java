package com.offway.core.inventory.infrastructure.probe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProbeResultTest {

    @Test
    void 조회에_실패했으면_못_쓰는_상태다() {
        assertTrue(ProbeResult.fail("TAGO 열차", "공공데이터포털", 500, "게이트웨이 오류", "").unusable());
    }

    @Test
    void 정상_응답은_못_쓰는_상태가_아니다() {
        assertFalse(ProbeResult.ok("TAGO 열차", "공공데이터포털", 200, "{}").unusable());
    }

    @Test
    void 키가_없어_건너뛴_것은_실패가_아니다() {
        assertFalse(
                ProbeResult.skipped("TMAP 경로", "SK openapi").unusable(),
                "키를 안 꽂은 로컬이 늘 장애로 보이면 그 신호는 죽는다");
    }

    @Test
    void 확인_불가는_실패로_세지_않는다() {
        assertFalse(
                ProbeResult.unverified("코레일", "공공데이터포털", "승인 대기").unusable(),
                "못 재본 것과 죽은 것은 다르다");
    }
}
