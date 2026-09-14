package com.offway.core.common.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * 게이트웨이 사유를 로그용 한 줄로(#569).
 *
 * <p><b>빈 문자열을 주는 것이 계약의 절반이다.</b> 호출자가 {@code "resultCode=%s%s"} 로 이어 붙이므로,
 * 평범한 실패에는 아무것도 안 붙어야 한다.
 */
class DataGoKrErrorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String of(String json) throws Exception {
        return DataGoKrError.of(MAPPER.readTree(json));
    }

    /** 실측한 인증키 장애 응답 그대로 — 지어낸 모양으로 잠그면 실제 장애에 안 걸린다. */
    @Test
    void 인증키_장애의_사유를_뽑는다() throws Exception {
        String body = """
                {
                  "OpenAPI_ServiceResponse": {
                    "cmmMsgHeader": {
                      "errMsg": "SERVICE_KEY_IS_NOT_REGISTERED_ERROR",
                      "returnAuthMsg": "등록되지 않은 서비스키",
                      "returnReasonCode": "30"
                    }
                  }
                }""";

        String message = of(body);

        assertTrue(message.contains("SERVICE_KEY_IS_NOT_REGISTERED_ERROR"), message);
        assertTrue(message.contains("30"), message);
    }

    /** 한도 소진도 같은 envelope 으로 온다 — 키 만료와 구분되려면 사유가 실려야 한다. */
    @Test
    void 한도_소진의_사유를_뽑는다() throws Exception {
        String body = """
                {"OpenAPI_ServiceResponse":{"cmmMsgHeader":{
                  "errMsg":"LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR","returnReasonCode":"22"}}}""";

        assertTrue(of(body).contains("LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"));
    }

    /** 평범한 응답에는 붙일 것이 없다 — 호출자가 그대로 이어 붙이므로 빈 문자열이어야 한다. */
    @Test
    void 붙일_것이_없으면_빈_문자열이다() throws Exception {
        assertEquals("", of("""
                {"response":{"header":{"resultCode":"22","resultMsg":"LIMITED"},"body":{}}}"""));
        assertEquals("", of("{}"));
    }

    /** 필드가 일부만 와도 터지지 않는다 — 로그를 만들다 예외를 내면 원래 사유가 묻힌다. */
    @Test
    void 필드가_빠져도_터지지_않는다() throws Exception {
        String body = """
                {"OpenAPI_ServiceResponse":{"cmmMsgHeader":{"returnAuthMsg":"무언가"}}}""";

        String message = of(body);

        assertTrue(message.contains("?"), message);
    }
}
