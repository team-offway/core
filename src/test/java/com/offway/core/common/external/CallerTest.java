package com.offway.core.common.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 외부 호출을 일으킨 주체(#285).
 *
 * <p>이 값이 곧 {@code external_api_call_caller} 의 PK 한 조각이자 디스코드 알림에 실리는 문구다.
 */
class CallerTest {

    @Test
    void 요청은_메서드와_패턴을_함께_싣는다() {
        // 같은 경로라도 메서드가 다르면 다른 화면이다.
        assertEquals("POST /api/v1/courses", Caller.request("POST", "/api/v1/courses").name());
    }

    /**
     * <b>패턴이라야 키 공간에 상한이 생긴다.</b> 경로를 그대로 쓰면 코스 id 마다 다른 주체가 되어 행이 무한히
     * 늘고, 알림 내역도 한 줄짜리 주체로 가득 찬다.
     */
    @Test
    void 패턴을_그대로_보존한다() {
        assertEquals("GET /api/v1/courses/{id}", Caller.request("GET", "/api/v1/courses/{id}").name());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    void 빈_이름은_미상으로_접는다(String blank) {
        // 빈 문자열이 PK 로 들어가면 "안 센 것" 과 "주체를 모르는 것" 이 한 행에서 섞인다.
        assertEquals(Caller.UNKNOWN, Caller.of(blank));
    }

    @Test
    void 앞뒤_공백을_턴다() {
        // 같은 주체가 공백 차이로 두 행이 되면 내역이 쪼개져 비중을 못 읽는다.
        assertEquals(Caller.of("코스생성"), Caller.of("  코스생성 "));
    }

    /**
     * 컬럼 폭을 넘기면 INSERT 가 통째로 실패해 <b>그 호출의 귀속이 사라진다.</b> 잘라서라도 남기는 편이 낫다.
     */
    @Test
    void 컬럼_폭을_넘으면_자른다() {
        String tooLong = "가".repeat(200);
        assertEquals(80, Caller.of(tooLong).name().length());
    }

    @Test
    void 이름이_null_이면_거절한다() {
        assertThrows(NullPointerException.class, () -> Caller.of(null));
    }

    // ── 배치인가 사용자 요청인가(#398) ────────────────────────────────────

    /**
     * <b>이 구분이 심사 자료의 핵심이다.</b> 총량만 보면 "우리가 API 를 쓴다" 까지밖에 못 말하는데,
     * 정작 보여야 하는 것은 서비스가 요청마다 실제로 부른다는 쪽이다.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "GET /api/v1/pois/{contentId}",
        "POST /api/v1/courses/generate",
        "PUT /api/v1/admin/policies/{id}",
        "PATCH /api/v1/admin/curated-links/{id}",
        "DELETE /api/v1/admin/policies/{id}",
    })
    void 요청이_만든_이름은_사용자_요청으로_센다(String name) {
        assertTrue(Caller.of(name).fromRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"축제기간배치", "갤러리사진배치", "장소운영시간배치", "지역방문자일별배치", "미상"})
    void 배치_이름은_사용자_요청이_아니다(String name) {
        assertFalse(Caller.of(name).fromRequest());
    }

    /**
     * 메서드처럼 <b>보이기만</b> 하는 이름에 속지 않는다.
     *
     * <p>판정 기준이 "메서드 + 공백" 이라, 공백 없이 이어 붙은 이름은 요청이 아니다.
     * {@link Caller#request} 가 반드시 공백을 넣기 때문이다.
     */
    @ParameterizedTest
    @ValueSource(strings = {"GETTING 어쩌구", "POSTBOX", "GET"})
    void 메서드처럼_보이는_이름은_요청이_아니다(String name) {
        assertFalse(Caller.of(name).fromRequest());
    }

    @Test
    void request_가_만든_것은_항상_요청이다() {
        assertTrue(Caller.request("GET", "/api/v1/home").fromRequest());
    }
}
