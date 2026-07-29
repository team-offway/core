package com.offway.core.transport.infrastructure.tago;

/**
 * TAGO(국토교통부, data.go.kr) 공통 요청 파라미터 키·고정값 — 여러 어댑터에 흩어진 문자열 리터럴을 한 곳에 모아 오타·변경 누락을
 * 막는다(외부 API 계약).
 */
final class TagoQuery {

    private TagoQuery() {}

    static final String SERVICE_KEY = "serviceKey";
    static final String RESPONSE_TYPE = "_type";
    static final String RESPONSE_TYPE_JSON = "json";
    static final String NUM_OF_ROWS = "numOfRows";
    static final String PAGE_NO = "pageNo";

    /** 응답은 첫 페이지만 쓴다(numOfRows 로 충분히 받는다). */
    static final int FIRST_PAGE = 1;
}
