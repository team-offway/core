package com.offway.core.trip.infrastructure.festival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCallRecorder;
import com.offway.core.trip.infrastructure.festival.dto.StandardFestival;
import com.offway.core.trip.infrastructure.festival.dto.StandardFestivalResult;
import java.lang.reflect.Method;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 표준데이터 응답을 <b>필드 하나하나까지</b> 옮기는가(#433).
 *
 * <h2>이 fixture 는 실제 응답이 아니다</h2>
 *
 * <p>포털이 영문 명세를 공개하지 않아 필드명을 <b>실호출로 확정하지 못했다.</b> 그래서 이 테스트가
 * 잠그는 것은 "필드명이 맞다" 가 아니라 <b>"필드명이 맞다면 열두 칸이 제자리로 가는가"</b> 다.
 *
 * <p>그래도 값어치가 있다. 파싱 로직의 실수(도로명·지번 우선순위, 시군구 추출, 날짜·숫자 형식,
 * items 가 한 겹 더 감싸 오는 경우)는 필드명과 무관하게 여기서 잡힌다. 실호출로 이름이 확정되면
 * 상수만 고치면 되고 이 테스트는 그대로 산다.
 *
 * <p><b>어댑터를 리플렉션으로 부른다.</b> {@code parse} 는 package-private 도 아닌 private 이고,
 * 그걸 열려고 WebClient·키·네트워크를 끌고 오면 이 테스트가 보려는 것(문자열 → 객체)과 무관한
 * 장치가 늘어난다.
 */
class FestivalStandardParsingTest {

    /** 우리가 기대하는 스키마로 만든 응답. 이름이 틀렸다면 이 fixture 도 함께 고쳐야 한다. */
    private static final String RESPONSE = """
            {"response":{"header":{"resultCode":"00"},"body":{
              "totalCount": 2,
              "items": [
                {
                  "fstvlNm": "안동국제탈춤페스티벌",
                  "opar": "탈춤공원 일원",
                  "fstvlStartDate": "2026-09-25",
                  "fstvlEndDate": "2026-10-04",
                  "fstvlCo": "탈춤 축제입니다",
                  "mnnstNm": "안동시",
                  "phoneNumber": "054-000-0000",
                  "homepageUrl": "https://example.kr",
                  "rdnmadr": "경상북도 안동시 육사로 239",
                  "lnmadr": "경상북도 안동시 운흥동 1",
                  "latitude": "36.5684",
                  "longitude": "128.7294"
                },
                {
                  "fstvlNm": "좌표없는축제",
                  "fstvlStartDate": "2026-09-25",
                  "fstvlEndDate": "2026-10-04",
                  "rdnmadr": "전라남도 신안군 어딘가",
                  "latitude": "",
                  "longitude": ""
                }
              ]}}}
            """;

    private static StandardFestivalResult parse(String body) throws Exception {
        FestivalStandardClientImpl client = new FestivalStandardClientImpl(
                null, (ExternalApiProperties) null, (ExternalApiCallRecorder) null);
        Method method = FestivalStandardClientImpl.class
                .getDeclaredMethod("parse", String.class, int.class);
        method.setAccessible(true);
        return (StandardFestivalResult) method.invoke(client, body, 1);
    }

    @Test
    void 열두_칸이_제자리로_간다() throws Exception {
        StandardFestivalResult result = parse(RESPONSE);

        assertEquals(2, result.totalCount());
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
        String body = RESPONSE.replace("\"rdnmadr\": \"경상북도 안동시 육사로 239\",", "\"rdnmadr\": \"\",");

        StandardFestival festival = parse(body).items().get(0);

        assertEquals("경상북도 안동시 운흥동 1", festival.address());
    }

    /** items 가 {@code {item:[...]}} 로 한 겹 더 감싸 오는 응답도 있다. */
    @Test
    void 한_겹_더_감싼_응답도_읽는다() throws Exception {
        String body = RESPONSE
                .replace("\"items\": [", "\"items\": {\"item\": [")
                .replace("]}}}", "]}}}}");

        assertEquals(1, parse(body).items().size());
    }

    /** 행이 하나뿐이면 배열이 아니라 객체로 오는 경우가 있다. */
    @Test
    void 한_건이_객체로_와도_읽는다() throws Exception {
        String body = """
                {"response":{"header":{"resultCode":"00"},"body":{
                  "totalCount": 1,
                  "items": {"item": {
                    "fstvlNm": "홑축제",
                    "fstvlStartDate": "2026-09-25",
                    "fstvlEndDate": "2026-10-04",
                    "rdnmadr": "경상북도 안동시 육사로 239",
                    "latitude": "36.5684",
                    "longitude": "128.7294"
                  }}}}}
                """;

        assertEquals(1, parse(body).items().size());
    }

    /**
     * <b>필드명이 틀리면 던진다.</b>
     *
     * <p>빈 결과를 돌려주면 호출자에게 "성공한 빈 페이지" 로 보이고, 다른 페이지가 저장되면 취소
     * 정리가 이번 회차를 온전한 것으로 판정한다 — 그러면 못 읽은 페이지의 축제들이 지워진다.
     */
    @Test
    void 축제명을_하나도_못_읽으면_던진다() {
        String body = """
                {"response":{"header":{"resultCode":"00"},"body":{
                  "totalCount": 1,
                  "items": [{"FSTVL_NM": "다른이름", "LATITUDE": "36.5"}]}}}
                """;

        Exception e = assertThrows(Exception.class, () -> parse(body));

        // 실제 키 목록이 메시지에 실려야 무엇으로 고칠지 로그가 바로 답한다.
        assertTrue(rootMessage(e).contains("FSTVL_NM"), rootMessage(e));
    }

    /**
     * <b>좌표가 없어 빠지는 것은 던지지 않는다.</b> 446건 중 101건이라 정상 상황이고, 그걸로 던지면
     * 좌표 없는 행만 모인 페이지에서 멀쩡한 적재가 멈춘다.
     */
    @Test
    void 좌표가_없어_전부_빠져도_던지지_않는다() throws Exception {
        String body = """
                {"response":{"header":{"resultCode":"00"},"body":{
                  "totalCount": 1,
                  "items": [{
                    "fstvlNm": "좌표없는축제",
                    "fstvlStartDate": "2026-09-25",
                    "fstvlEndDate": "2026-10-04",
                    "rdnmadr": "전라남도 신안군 어딘가"
                  }]}}}
                """;

        StandardFestivalResult result = parse(body);

        assertTrue(result.items().isEmpty());
        assertEquals(1, result.totalCount());
    }

    @Test
    void 결과가_없으면_빈_목록이다() throws Exception {
        String body = """
                {"response":{"header":{"resultCode":"00"},"body":{"totalCount": 0}}}
                """;

        assertTrue(parse(body).items().isEmpty());
    }

    @Test
    void 성공_코드가_아니면_던진다() {
        String body = """
                {"response":{"header":{"resultCode":"99"},"body":{"totalCount": 0}}}
                """;

        assertThrows(Exception.class, () -> parse(body));
    }

    /** 선택 값은 없으면 null 이다 — 빈 문자열을 그대로 담지 않는다. */
    @Test
    void 빈_선택_값은_null_이다() throws Exception {
        String body = RESPONSE.replace("\"opar\": \"탈춤공원 일원\",", "\"opar\": \"  \",");

        assertNull(parse(body).items().get(0).venue());
    }

    private static String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return String.valueOf(cause.getMessage());
    }
}
