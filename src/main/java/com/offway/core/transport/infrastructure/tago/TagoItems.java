package com.offway.core.transport.infrastructure.tago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * data.go.kr(TAGO) 공통 응답 봉투를 푼 결과. 세 상태를 구분해, 각 어댑터가 자기 도메인 타입으로 옮기기만 하면 되게 한다.
 *
 * <p>TAGO 응답에는 어댑터마다 반복되는 함정이 셋 있다.
 *
 * <ul>
 *   <li>{@code resultCode} 가 {@code "00"} 이 아니면 HTTP 200 이어도 실패다.
 *   <li>결과가 없으면 {@code items} 가 빈 객체가 아니라 <b>빈 문자열</b>로 온다.
 *   <li>결과가 하나면 {@code item} 이 배열이 아니라 <b>객체 하나</b>로 온다.
 * </ul>
 *
 * <p>세 곳에 같은 방어 코드를 복사하지 않도록 여기 모은다. "결과 없음"과 "조회 실패"를 뭉뚱그리지 않는 게 핵심이다 — 전자는
 * 사용자에게 안내할 정상 결과고 후자는 조용히 폴백해야 한다.
 */
sealed interface TagoItems {

    String SUCCESS_CODE = "00";

    /** 항목이 하나 이상 있다. */
    record Items(List<JsonNode> nodes) implements TagoItems {}

    /** 조회는 정상인데 결과가 없다. */
    record Empty() implements TagoItems {}

    /** 조회 실패(비정상 resultCode) — 결과 없음과 구분한다. */
    record Failed() implements TagoItems {}

    /**
     * TAGO 응답 본문을 파싱한다.
     *
     * @throws Exception JSON 파싱 실패. 호출자가 잡아 자기 도메인의 "조회 불가" 로 옮긴다
     */
    static TagoItems parse(String body, ObjectMapper objectMapper) throws Exception {
        JsonNode response = objectMapper.readTree(body).path("response");
        if (!SUCCESS_CODE.equals(response.path("header").path("resultCode").asText())) {
            return new Failed();
        }
        JsonNode item = response.path("body").path("items").path("item");
        if (item.isMissingNode() || item.isNull()) {
            return new Empty();
        }
        List<JsonNode> nodes = (item.isArray()
                        ? StreamSupport.stream(item.spliterator(), false)
                        : Stream.of(item))
                .toList();
        return nodes.isEmpty() ? new Empty() : new Items(nodes);
    }
}
