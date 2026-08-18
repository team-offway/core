package com.offway.core.common.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
