package com.offway.core.common.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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
    void data가_출처를_밝히면_기관명까지_함께_나간다() {
        // 이름만 주면 출처가 하나 늘 때 앱이 배포돼야 화면에 뜬다. 표기 누락은 규정 위반이라 그 공백이
        // 그대로 위반이 된다.
        ApiResponseBody<Attributed응답> body =
                ApiResponseBody.ok(new Attributed응답("값", Set.of(DataSource.KTO, DataSource.KHS)));

        assertEquals(
                List.of(new DataSourceResponse("KTO", "한국관광공사"), new DataSourceResponse("KHS", "국가유산청")),
                body.sources());
    }

    /**
     * 순서가 <b>요청마다 바뀌지 않는다</b>.
     *
     * <p>{@code Set} 을 그대로 실으면 같은 화면에서 출처 차례가 흔들린다. enum 선언 순서를 쓰면 가장
     * 무겁게 표기해야 하는 한국관광공사가 늘 앞에 온다.
     */
    @Test
    void 출처_순서는_선언_순서다() {
        ApiResponseBody<Attributed응답> body = ApiResponseBody.ok(
                new Attributed응답("값", Set.of(DataSource.KASI, DataSource.LOCAL_PERMIT, DataSource.KTO)));

        assertEquals(List.of("KTO", "LOCAL_PERMIT", "KASI"), body.sources().stream().map(DataSourceResponse::key).toList());
    }

    @Test
    void 모든_출처가_기관명을_들고_있다() {
        // 라벨을 빠뜨린 상수가 있으면 그 출처만 화면에서 빈칸으로 나간다.
        for (DataSource source : DataSource.values()) {
            assertFalse(source.label().isBlank(), source + " 의 기관명이 비었다");
            assertFalse(source.detail().isBlank(), source + " 의 활용 내역이 비었다");
        }
    }

    /**
     * <b>목록 응답도 출처를 잃지 않는다</b>(#399).
     *
     * <p>코스 목록처럼 {@code data} 가 {@code List} 인 응답이 있다. 그 자리를 안 보면 목록 화면에서만
     * 표기가 사라지는데, 응답은 멀쩡해 보이고 빠진 것은 표기뿐이라 눈에 안 띈다.
     */
    @Test
    void 목록이_실려도_원소의_출처를_모은다() {
        ApiResponseBody<List<Attributed응답>> body = ApiResponseBody.ok(List.of(
                new Attributed응답("가", Set.of(DataSource.KTO)),
                new Attributed응답("나", Set.of(DataSource.KMA)),
                new Attributed응답("다", Set.of())));

        assertEquals(List.of("KTO", "KMA"), body.sources().stream().map(DataSourceResponse::key).toList());
    }

    @Test
    void 출처를_안_밝히는_원소만_든_목록은_비어_있다() {
        ApiResponseBody<List<평범한응답>> body = ApiResponseBody.ok(List.of(new 평범한응답("값")));

        assertTrue(body.sources().isEmpty());
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

        assertEquals(List.of(new DataSourceResponse("KMA", "기상청")), body.sources());
    }

    @Test
    void 페이지_응답도_출처를_잃지_않는다() {
        ApiResponseBody<Attributed응답> body = ApiResponseBody.ok(
                new Attributed응답("값", Set.of(DataSource.LOCAL_PERMIT)), new PageResponse(0, 20, 1, 1));

        assertEquals(List.of(new DataSourceResponse("LOCAL_PERMIT", "지방행정인허가데이터개방")), body.sources());
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
