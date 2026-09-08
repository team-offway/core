package com.offway.core.trip.infrastructure.camping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.external.ExternalApiCallRecorder;
import com.offway.core.trip.infrastructure.camping.dto.GoCampsite;
import com.offway.core.trip.infrastructure.camping.dto.GoCampsiteResult;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 고캠핑 응답을 <b>필드 하나하나까지</b> 옮기는가(#510).
 *
 * <h2>fixture 가 실제 응답이다</h2>
 *
 * <p>축제(#506)에서 필드명을 <b>추측했다가 틀렸다</b> — 우리가 기대한 모양으로 만든 fixture 는 그 실수를
 * 못 잡았다. 아래 두 건은 2026-09-08 실호출에서 그대로 떠 온 것이고(387 국립남해편백자연휴양림 ·
 * 7389 가리왕산 자건재), 이 파일이 잠그는 것은 <b>실제 필드명</b>이다.
 *
 * <p>주소를 보라 — 하나는 {@code 경상남도}, 다른 하나는 <b>{@code 강원}</b> 이다. 개편 전후와 축약형이
 * 섞여 오므로 지역 매칭은 별칭 표를 거쳐야 한다({@code RegionNameMatcher}).
 *
 * <p><b>어댑터를 리플렉션으로 부른다.</b> {@code parse} 는 private 이고, 그걸 열려고 키·네트워크를
 * 끌고 오면 이 테스트가 보려는 것(문자열 → 객체)과 무관한 장치가 늘어난다.
 */
class GoCampingParsingTest {

    /** 실호출 응답 두 건 — 운영 중인 것 하나(사진·소개·운영정보 전부), 휴장 하나. */
    private static final String RESPONSE = """
            {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{
              "numOfRows": 4000, "pageNo": 1, "totalCount": 2,
              "items": {"item": [
                {
                  "contentId": "387",
                  "facltNm": "국립남해편백자연휴양림",
                  "addr1": "경상남도 남해군 삼동면 금암로 658 ",
                  "sigunguNm": "남해군",
                  "doNm": "경상남도",
                  "mapX": "128.020135238357",
                  "mapY": "34.7521440529408",
                  "induty": "일반야영장",
                  "firstImageUrl": "https://gocamping.or.kr/upload/camp/387/thumb/thumb_720_45112.jpg",
                  "lineIntro": "편백나무 숲에서 피톤치드 맡으며 건강하게 캠핑을 하는 곳.",
                  "intro": "남해 편백자연휴양림은 명칭 그대로 남해 바다 인근에 위치하고 있으며",
                  "tel": "055-867-7881",
                  "homepage": "https://www.foresttrip.go.kr/",
                  "operPdCl": "봄,여름,가을,겨울",
                  "operDeCl": "평일+주말",
                  "resveCl": "온라인실시간예약",
                  "manageSttus": "운영"
                },
                {
                  "contentId": "7389",
                  "facltNm": "가리왕산 자건재",
                  "addr1": "강원 정선군 정선읍 청량길 14-2",
                  "sigunguNm": "정선군",
                  "doNm": "강원도",
                  "mapX": "128.5767607",
                  "mapY": "37.4232491",
                  "induty": "일반야영장",
                  "firstImageUrl": "https://gocamping.or.kr/upload/camp/7389/thumb/thumb_720_8440.jpg",
                  "lineIntro": "",
                  "intro": "",
                  "tel": "033-563-0303",
                  "homepage": "",
                  "operPdCl": "",
                  "operDeCl": "",
                  "resveCl": "",
                  "manageSttus": "휴장"
                }
              ]}}}}
            """;

    private static GoCampsiteResult parse(String body) throws Exception {
        GoCampingClientImpl client = new GoCampingClientImpl(
                WebClient.builder().build(), (ExternalApiProperties) null, (ExternalApiCallRecorder) null);
        Method method = GoCampingClientImpl.class.getDeclaredMethod("parse", String.class);
        method.setAccessible(true);
        return (GoCampsiteResult) method.invoke(client, body);
    }

    @Test
    void 열여섯_칸이_제자리로_간다() throws Exception {
        GoCampsiteResult result = parse(RESPONSE);

        assertEquals(2, result.totalCount());
        assertEquals(1, result.items().size(), "휴장한 야영장은 빠진다");

        GoCampsite campsite = result.items().get(0);
        assertEquals("387", campsite.externalId());
        assertEquals("국립남해편백자연휴양림", campsite.name());
        assertEquals("경상남도 남해군 삼동면 금암로 658", campsite.address(), "꼬리 공백이 잘린다");
        assertEquals("남해군", campsite.sigunguName());
        assertEquals(34.7521440529408, campsite.lat());
        assertEquals(128.020135238357, campsite.lng());
        assertEquals("일반야영장", campsite.induty());
        assertEquals("https://gocamping.or.kr/upload/camp/387/thumb/thumb_720_45112.jpg", campsite.imageUrl());
        assertEquals("편백나무 숲에서 피톤치드 맡으며 건강하게 캠핑을 하는 곳.", campsite.lineIntro());
        assertEquals("055-867-7881", campsite.tel());
        assertEquals("https://www.foresttrip.go.kr/", campsite.homepageUrl());
        assertEquals("봄,여름,가을,겨울", campsite.operPeriod());
        assertEquals("평일+주말", campsite.operDays());
        assertEquals("온라인실시간예약", campsite.reservation());
        assertTrue(campsite.operating());
    }

    /**
     * <b>위경도가 바뀌어 있지 않은가.</b> {@code mapX} 가 경도이고 {@code mapY} 가 위도인데, 이름만
     * 보면 x 를 위도로 읽기 쉽다. 바꿔 읽으면 한국 좌표계에서는 <b>범위 밖으로 떨어져 전부 버려진다</b> —
     * "야영장 0건" 이 조용히 나온다.
     */
    @Test
    void mapX가_경도이고_mapY가_위도다() throws Exception {
        GoCampsite campsite = parse(RESPONSE).items().get(0);

        assertTrue(campsite.lat() < campsite.lng(), "우리 위도는 33~39, 경도는 124~132 다");
    }

    /**
     * <b>필드명이 틀리면 던진다.</b>
     *
     * <p>빈 결과를 돌려주면 호출자에게 "성공한 빈 회차" 로 보이고, 그러면 정리가 이번 회차를 온전한
     * 것으로 판정해 <b>멀쩡한 야영장을 지운다</b>. 축제가 정확히 그렇게 조용히 0건이 됐다(#506).
     */
    @Test
    void 야영장명을_하나도_못_읽으면_던진다() {
        String body = RESPONSE.replace("\"facltNm\"", "\"FACLT_NM\"");

        Exception e = assertThrows(Exception.class, () -> parse(body));

        // 실제 키 목록이 메시지에 실려야 무엇으로 고칠지 로그가 바로 답한다.
        assertTrue(rootMessage(e).contains("FACLT_NM"), rootMessage(e));
    }

    /**
     * <b>휴장이라 전부 빠지는 것은 던지지 않는다.</b> 이름은 읽혔으므로 필드명 문제가 아니다 — 그걸로
     * 던지면 휴장만 모인 응답에서 멀쩡한 적재가 멈춘다.
     */
    @Test
    void 휴장이라_전부_빠져도_던지지_않는다() throws Exception {
        String body = RESPONSE.replace("\"운영\"", "\"휴장\"");

        GoCampsiteResult result = parse(body);

        assertTrue(result.items().isEmpty());
        assertEquals(2, result.totalCount());
    }

    /** 결과가 없으면 items 가 빈 문자열로 온다 — data.go.kr 계열의 오랜 함정이다. */
    @Test
    void items가_빈_문자열이면_빈_목록이다() throws Exception {
        String body = """
                {"response":{"header":{"resultCode":"0000"},"body":{"totalCount": 0, "items": ""}}}
                """;

        assertTrue(parse(body).items().isEmpty());
    }

    /** 한 건뿐이면 item 이 배열이 아니라 객체로 온다. */
    @Test
    void 한_건이_객체로_와도_읽는다() throws Exception {
        String body = """
                {"response":{"header":{"resultCode":"0000"},"body":{
                  "totalCount": 1,
                  "items": {"item": {
                    "contentId": "387",
                    "facltNm": "홑야영장",
                    "addr1": "경상남도 남해군 삼동면",
                    "mapX": "128.02", "mapY": "34.75",
                    "manageSttus": "운영"
                  }}}}}
                """;

        assertEquals(1, parse(body).items().size());
    }

    @Test
    void 성공_코드가_아니면_던진다() {
        String body = """
                {"response":{"header":{"resultCode":"30","resultMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR"},
                 "body":{"totalCount": 0}}}
                """;

        assertThrows(Exception.class, () -> parse(body));
    }

    /** 선택 값은 없으면 null 이다 — 빈 문자열을 그대로 담지 않는다. */
    @Test
    void 빈_선택값은_null_이다() throws Exception {
        String body = RESPONSE.replace("\"tel\": \"055-867-7881\",", "\"tel\": \"  \",");

        assertEquals(null, parse(body).items().get(0).tel());
    }

    private static String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return String.valueOf(cause.getMessage());
    }
}
