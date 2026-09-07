package com.offway.core.common.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link ExternalHttpOutcome} 단위 테스트.
 *
 * <p>가르는 기준은 <b>"이 요청 하나의 문제인가, 그 API 전체가 막힌 것인가"</b> 다. 2026-09-07 운영 키가
 * 403 을 받는 동안 알림이 한 줄도 안 갔던 것이 이 구분을 안 해서였다(#489).
 */
class ExternalHttpOutcomeTest {

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429})
    void 자격이나_한도로_막힌_것은_장애로_센다(int httpStatus) {
        ExternalHttpOutcome outcome = ExternalHttpOutcome.of(httpStatus);

        assertEquals(ExternalHttpOutcome.BLOCKED, outcome);
        assertTrue(outcome.isFailure(), "다음 요청도 똑같이 막힌다 — 사용자에겐 외부가 죽은 것과 같다");
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 409, 422})
    void 요청_하나가_잘못된_것은_장애가_아니다(int httpStatus) {
        ExternalHttpOutcome outcome = ExternalHttpOutcome.of(httpStatus);

        assertEquals(ExternalHttpOutcome.BAD_REQUEST, outcome);
        assertFalse(outcome.isFailure(), "없는 장소를 물어 404 가 오는 건 정상 동작이다");
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 504})
    void 외부가_못_하겠다고_답하면_장애다(int httpStatus) {
        assertTrue(ExternalHttpOutcome.of(httpStatus).isFailure());
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 201, 204, 302})
    void 정상_응답은_장애가_아니다(int httpStatus) {
        assertEquals(ExternalHttpOutcome.OK, ExternalHttpOutcome.of(httpStatus));
    }

    /** 다시 묻는 것과 알리는 것은 다른 질문이다 — 막힌 것은 알리되 다시 묻지는 않는다. */
    @Test
    void 막힌_것은_알리되_다시_묻지는_않는다() {
        ExternalHttpOutcome blocked = ExternalHttpOutcome.of(403);

        assertTrue(blocked.isFailure(), "알림은 가야 한다");
        assertFalse(blocked.worthRetrying(), "몇 번을 더 물어도 같은 답이라 확인 호출은 아낀다");
    }

    @Test
    void 서버_오류는_다시_물어_일시적인지_가른다() {
        assertTrue(ExternalHttpOutcome.of(503).worthRetrying());
    }

    /**
     * 프로브의 상태 코드는 순수 HTTP 코드가 아니다 — 응답이 없으면 0 이고, 외부가 200 에 실패를 실어
     * 보내기도 한다(#479). 둘 다 코드만으로 사유를 못 가르므로 다시 묻는다.
     */
    @Test
    void 코드로_사유를_못_가르는_경우는_다시_묻는다() {
        assertTrue(ExternalHttpOutcome.of(0).worthRetrying(), "응답 자체가 없었던 경우");
        assertTrue(ExternalHttpOutcome.of(200).worthRetrying(), "200 에 실린 resultCode 실패");
    }

    @Test
    void 요청이_잘못된_것은_다시_묻지_않는다() {
        assertFalse(ExternalHttpOutcome.of(404).worthRetrying());
    }

    /** 받는 사람이 할 일이 갈린다 — 키 문제는 포털에서 활용신청을 봐야 하고, 서버 오류는 기다릴 일이다. */
    @Test
    void 막힌_경우는_사유를_따로_적는다() {
        assertTrue(ExternalHttpOutcome.of(403).describe(403).contains("키가 막혔습니다"));
        assertFalse(ExternalHttpOutcome.of(502).describe(502).contains("키가 막혔습니다"));
    }
}
