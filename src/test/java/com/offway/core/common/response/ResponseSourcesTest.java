package com.offway.core.common.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 출처가 <b>응답 래퍼까지 실제로 닿는지</b>(#399).
 *
 * <p>여기서 잠그는 것은 접점 하나다 — DTO 가 {@link Attributed} 를 구현하면 래퍼가 집어가고, 아니면
 * 빈 집합이 나간다. 그래서 컨트롤러가 한 줄도 안 바뀐다는 것이 이 설계의 전제인데, 접점이 조용히
 * 끊기면 <b>표기가 통째로 사라지고도 응답은 멀쩡해 보인다.</b>
 */
class ResponseSourcesTest {

    private record Attributed응답(String value, Set<DataSource> sources) implements Attributed {
    }

    private record 평범한응답(String value) {
    }

    @Test
    void data가_출처를_밝히면_래퍼가_싣는다() {
        ApiResponseBody<Attributed응답> body =
                ApiResponseBody.ok(new Attributed응답("값", Set.of(DataSource.KTO, DataSource.KHS)));

        assertEquals(Set.of(DataSource.KTO, DataSource.KHS), body.sources());
    }

    @Test
    void 출처를_안_밝히는_data는_빈_집합이다() {
        // null 이 아니라 빈 집합이다 — 앱이 유무를 분기하지 않게.
        ApiResponseBody<평범한응답> body = ApiResponseBody.ok(new 평범한응답("값"));

        assertTrue(body.sources().isEmpty());
    }

    @Test
    void data가_없는_성공에도_출처는_비어_있다() {
        assertTrue(ApiResponseBody.ok().sources().isEmpty());
        assertTrue(ApiResponseBody.okWithDetail("탈퇴했습니다").sources().isEmpty());
    }

    @Test
    void created도_같은_규칙을_지난다() {
        // ok 만 고치고 created 를 빠뜨리면 생성 응답에서만 표기가 사라진다.
        ApiResponseBody<Attributed응답> body =
                ApiResponseBody.created(new Attributed응답("값", Set.of(DataSource.KMA)));

        assertEquals(Set.of(DataSource.KMA), body.sources());
    }

    @Test
    void 페이지_응답도_출처를_잃지_않는다() {
        ApiResponseBody<Attributed응답> body = ApiResponseBody.ok(
                new Attributed응답("값", Set.of(DataSource.LOCAL_PERMIT)), new PageResponse(0, 20, 1, 1));

        assertEquals(Set.of(DataSource.LOCAL_PERMIT), body.sources());
    }

    /**
     * 실패 응답에는 출처가 없다.
     *
     * <p>내려간 데이터가 없으니 빌려온 값도 없다. 여기에 출처가 실리면 <b>안 쓴 기관을 표기</b>하게 된다.
     */
    @Test
    void 실패_응답에는_출처가_없다() {
        ApiResponseBody<Void> body = ApiResponseBody.fail(new TestErrorCode());

        assertTrue(body.sources().isEmpty());
        assertFalse(body.code().equals("OK"));
    }

    private record TestErrorCode() implements com.offway.core.common.exception.ErrorCode {

        @Override
        public String code() {
            return "TEST-001";
        }

        @Override
        public com.offway.core.common.exception.ErrorCategory category() {
            return com.offway.core.common.exception.ErrorCategory.BAD_REQUEST;
        }

        @Override
        public String message() {
            return "테스트";
        }
    }
}
