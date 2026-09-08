package com.offway.core.inventory.infrastructure.probe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    /**
     * <b>4xx 는 다시 물어봐야 소용없다</b>(CodeRabbit #477 리뷰).
     *
     * <p>키가 안 꽂혔거나 파라미터가 틀린 것이라 몇 번을 더 불러도 같은 답이 온다. 확인 호출은
     * "일시적인가 진짜 죽었나" 를 가르려는 것인데 4xx 에는 가를 것이 없고, 그 호출이 공유 키의 한도만
     * 태운다.
     */
    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 429})
    void 요청이_잘못된_것이면_다시_묻지_않는다(int httpStatus) {
        ProbeResult result = ProbeResult.fail("국문관광정보", "공공데이터포털", httpStatus, "요청 오류", "");

        assertTrue(result.unusable(), "못 쓰는 상태인 것은 맞다 — 어드민 표에는 실패로 보여야 한다");
        assertFalse(result.worthConfirming(), "몇 번을 물어도 같은 답이라 한도만 태운다");
    }

    /** 5xx·무응답은 일시적일 수 있어 그 자리에서 더 묻는다. */
    @ParameterizedTest
    @ValueSource(ints = {0, 500, 502, 503})
    void 외부가_못_하겠다고_답했으면_다시_묻는다(int httpStatus) {
        ProbeResult result = ProbeResult.fail("국문관광정보", "공공데이터포털", httpStatus, "게이트웨이 오류", "");

        assertTrue(result.worthConfirming());
    }

    /**
     * <b>키가 없거나 못 재본 것은 상태 판정에서 뺀다</b>(#479).
     *
     * <p>외부에 대해 아무것도 말해주지 않는 결과다. 성공으로 세면 죽은 API 가 살아 있는 것으로 보이고,
     * 실패로 세면 키를 안 꽂은 환경이 늘 장애가 된다.
     */
    @Test
    void 재본_것만_상태_판정에_쓴다() {
        assertTrue(ProbeResult.ok("TAGO 열차", "공공데이터포털", 200, "{}").observed());
        assertTrue(ProbeResult.fail("TAGO 열차", "공공데이터포털", 200, "resultCode 실패", "").observed());

        assertFalse(ProbeResult.skipped("TMAP 경로", "SK openapi").observed());
        assertFalse(ProbeResult.unverified("코레일", "공공데이터포털", "승인 대기").observed());
    }

    /**
     * <b>200 인데 실패인 것</b>이 이 작업의 핵심이다(#479).
     *
     * <p>공공데이터포털은 키가 만료되거나 한도를 태우면 HTTP 200 에 resultCode 만 실패로 실어 보낸다.
     * HTTP 계층만 보는 필터에게는 성공으로 보이지만, 실제로는 아무것도 못 가져오는 상태다.
     */
    @Test
    void HTTP_200_이라도_resultCode_실패는_못_쓰는_상태다() {
        ProbeResult result = ProbeResult.fail(
                "국문관광정보", "공공데이터포털", 200, "정상 코드(resultCode 00) 없음 — 키/승인/파라미터 확인", "");

        assertTrue(result.unusable(), "200 이라고 성공으로 세면 한도를 태운 날 '전부 정상' 을 보게 된다");
        assertTrue(result.observed(), "재본 결과다 — 판정에 써야 한다");
        assertTrue(result.worthConfirming(), "일시적일 수 있어 그 자리에서 더 물어본다");
    }

    /** 실패가 아닌 것은 애초에 확인 대상이 아니다. */
    @Test
    void 실패가_아니면_다시_묻지_않는다() {
        assertFalse(ProbeResult.ok("TAGO 열차", "공공데이터포털", 200, "{}").worthConfirming());
        assertFalse(ProbeResult.skipped("TMAP 경로", "SK openapi").worthConfirming());
        assertFalse(ProbeResult.unverified("코레일", "공공데이터포털", "승인 대기").worthConfirming());
    }
}
