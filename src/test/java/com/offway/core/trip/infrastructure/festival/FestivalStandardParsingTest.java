package com.offway.core.trip.infrastructure.festival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.external.ExternalApiCallRecorder;
import com.offway.core.trip.infrastructure.festival.dto.StandardFestival;
import com.offway.core.trip.infrastructure.festival.dto.StandardFestivalResult;
import java.lang.reflect.Method;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 표준데이터 응답을 <b>필드 하나하나까지</b> 옮기는가(#433).
 *
 * <h2>이 fixture 는 실제 응답이다</h2>
 *
 * <p>2026-09-07 에 파일 주소를 직접 호출해 받은 모양 그대로다. 예전에는 포털이 영문 명세를 공개하지
 * 않아 필드명을 추측했고({@code fstvlNm} 같은 camelCase) <b>그게 틀렸다</b> — 실제로는
 * {@code FSTVL_NM} 처럼 영문 대문자다. 운영에서 축제가 0건이던 이유의 절반이 그것이었다.
 *
 * <p>응답은 {@code {response:{body:{items:…}}}} 래핑도 {@code resultCode} 도 없는 <b>평평한 배열</b>
 * 이다. 그래서 "한 겹 더 감싸 오는 경우"·"한 건이 객체로 오는 경우"·"성공 코드가 아닌 경우" 를 보던
 * 테스트들이 함께 사라졌다 — 오픈API 계열의 사정이고 파일에는 없다.
 *
 * <p><b>어댑터를 리플렉션으로 부른다.</b> {@code parse} 는 private 이고, 그걸 열려고 WebClient·
 * 네트워크를 끌고 오면 이 테스트가 보려는 것(문자열 → 객체)과 무관한 장치가 늘어난다.
 */
class FestivalStandardParsingTest {

    /** 실호출로 받은 모양. 좌표 없는 행이 섞여 오는 것도 실제 그대로다(1,305건 중 225건). */
    private static final String RESPONSE = """
            [
              {
                "FSTVL_NM": "안동국제탈춤페스티벌",
                "OPAR": "탈춤공원 일원",
                "FSTVL_START_DATE": "2026-09-25",
                "FSTVL_END_DATE": "2026-10-04",
                "FSTVL_CO": "탈춤 축제입니다",
                "MNNST_NM": "안동시",
                "PHONE_NUMBER": "054-000-0000",
                "HOMEPAGE_URL": "https://example.kr",
                "RDNMADR": "경상북도 안동시 육사로 239",
                "LNMADR": "경상북도 안동시 운흥동 1",
                "LATITUDE": "36.5684",
                "LONGITUDE": "128.7294"
              },
              {
                "FSTVL_NM": "좌표없는축제",
                "FSTVL_START_DATE": "2026-09-25",
                "FSTVL_END_DATE": "2026-10-04",
                "RDNMADR": "전남광주통합특별시 신안군 어딘가",
                "LATITUDE": "",
                "LONGITUDE": ""
              }
            ]
            """;

    private static StandardFestivalResult parse(String body) throws Exception {
        FestivalStandardClientImpl client =
                new FestivalStandardClientImpl(null, (ExternalApiCallRecorder) null);
        Method method = FestivalStandardClientImpl.class.getDeclaredMethod("parse", String.class);
        method.setAccessible(true);
        return (StandardFestivalResult) method.invoke(client, body);
    }

    @Test
    void 열두_칸이_제자리로_간다() throws Exception {
        StandardFestivalResult result = parse(RESPONSE);

        assertEquals(2, result.totalCount(), "받은 행 수다 — 쓸 수 있는 수와 다르다");
        assertEquals(1, result.items().size(), "좌표 없는 행은 쓸 수 없어 빠진다");

        StandardFestival festival = result.items().get(0);
        assertEquals("안동국제탈춤페스티벌", festival.name());
        assertEquals("탈춤공원 일원", festival.venue());
        assertEquals(LocalDate.of(2026, 9, 25), festival.eventStart());
        assertEquals(LocalDate.of(2026, 10, 4), festival.eventEnd());
        assertEquals("탈춤 축제입니다", festival.description());
        assertEquals("안동시", festival.host());
        assertEquals("054-000-0000", festival.tel());
        assertEquals("https://example.kr", festival.homepageUrl());
        assertEquals(36.5684, festival.lat());
        assertEquals(128.7294, festival.lng());
    }

    /** 주소는 <b>도로명이 먼저</b>다 — 지자체마다 채우는 칸이 달라 하나만 보면 빈다. */
    @Test
    void 도로명이_있으면_도로명을_쓴다() throws Exception {
        StandardFestival festival = parse(RESPONSE).items().get(0);

        assertEquals("경상북도 안동시 육사로 239", festival.address());
        assertEquals("안동시", festival.sigunguName(), "지역 매칭은 주소 둘째 토큰으로 한다");
    }

    @Test
    void 도로명이_없으면_지번을_쓴다() throws Exception {
        String body = RESPONSE.replace("\"RDNMADR\": \"경상북도 안동시 육사로 239\",", "\"RDNMADR\": \"\",");

        StandardFestival festival = parse(body).items().get(0);

        assertEquals("경상북도 안동시 운흥동 1", festival.address());
    }

    /**
     * <b>필드명이 틀리면 던진다.</b>
     *
     * <p>빈 결과를 돌려주면 호출자에게 "성공한 빈 회차" 로 보이고, 취소 정리가 그것을 온전한 회차로
     * 판정한다 — 그러면 멀쩡한 축제들이 취소로 간주돼 지워진다.
     *
     * <p>이 방어가 실제로 값어치를 했다. 예전 코드가 camelCase 를 기대했는데 원본은 대문자였고,
     * 그때 이 검사가 없었다면 "축제 0건" 이 정상처럼 보였을 것이다.
     */
    @Test
    void 축제명을_하나도_못_읽으면_던진다() {
        String body = """
                [{"festivalName": "다른이름", "LATITUDE": "36.5"}]
                """;

        Exception e = assertThrows(Exception.class, () -> parse(body));

        // 실제 키 목록이 메시지에 실려야 무엇으로 고칠지 로그가 바로 답한다.
        assertTrue(rootMessage(e).contains("festivalName"), rootMessage(e));
    }

    /**
     * <b>좌표가 없어 빠지는 것은 던지지 않는다.</b> 1,305건 중 225건(17%)이라 정상 상황이고, 그걸로
     * 던지면 멀쩡한 적재가 멈춘다.
     */
    @Test
    void 좌표가_없어_전부_빠져도_던지지_않는다() throws Exception {
        String body = """
                [{
                  "FSTVL_NM": "좌표없는축제",
                  "FSTVL_START_DATE": "2026-09-25",
                  "FSTVL_END_DATE": "2026-10-04",
                  "RDNMADR": "전남광주통합특별시 신안군 어딘가"
                }]
                """;

        StandardFestivalResult result = parse(body);

        assertTrue(result.items().isEmpty());
        assertEquals(1, result.totalCount(), "받기는 받았다 — 그 사실이 남아야 한다");
    }

    @Test
    void 결과가_없으면_빈_목록이다() throws Exception {
        assertTrue(parse("[]").items().isEmpty());
    }

    /**
     * <b>배열이 아니면 던진다.</b>
     *
     * <p>파일 주소는 성공하면 언제나 평평한 배열을 준다. 객체가 왔다면 오류 응답이거나 주소 규칙이
     * 바뀐 것이라, 조용히 빈 결과로 넘기면 그 회차가 온전한 것으로 판정돼 축제가 지워진다.
     */
    @Test
    void 배열이_아니면_던진다() {
        assertThrows(Exception.class, () -> parse("{\"message\":\"error\"}"));
    }

    /** 리플렉션 호출은 예외를 감싸므로 원인 체인의 끝에서 메시지를 꺼낸다. */
    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return String.valueOf(cause.getMessage());
    }
}
