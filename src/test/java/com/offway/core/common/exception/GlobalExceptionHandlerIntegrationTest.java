package com.offway.core.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.common.response.ApiResponseBody;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class GlobalExceptionHandlerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 도메인_예외는_errorCode의_status와_code로_응답한다() throws Exception {
        mockMvc.perform(get("/test/exception/domain"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("TEST-001"))
                .andExpect(jsonPath("$.detail").value("이미 처리된 요청입니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 외부의존성_예외는_502로_응답한다() throws Exception {
        mockMvc.perform(get("/test/exception/external"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.code").value("TEST-002"));
    }

    @Test
    void 검증_실패는_400과_필드_메시지로_응답한다() throws Exception {
        mockMvc.perform(post("/test/exception/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"annualLeaveDays\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("COMMON-400"))
                .andExpect(jsonPath("$.detail").value("annualLeaveDays: 0 이상이어야 합니다."));
    }

    @Test
    void 예상하지_못한_예외는_500과_공통코드로_응답한다() throws Exception {
        mockMvc.perform(get("/test/exception/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("COMMON-500"))
                .andExpect(jsonPath("$.detail").value(CommonErrorCode.INTERNAL_ERROR.message()));
    }

    @Test
    void 내부_예외_메시지는_응답에_새지_않는다() throws Exception {
        mockMvc.perform(get("/test/exception/unexpected"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("커넥션 풀"))));
    }

    @Test
    void 정상_응답은_OK코드를_싣는다() throws Exception {
        mockMvc.perform(post("/test/exception/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"annualLeaveDays\": 3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void 깨진_JSON_본문은_400으로_응답한다() throws Exception {
        mockMvc.perform(post("/test/exception/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"annualLeaveDays\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    void 본문_타입이_맞지_않으면_400으로_응답한다() throws Exception {
        mockMvc.perform(post("/test/exception/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"annualLeaveDays\": \"문자열\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    void 본문이_없으면_400으로_응답한다() throws Exception {
        mockMvc.perform(post("/test/exception/validated").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    void 지원하지_않는_메서드는_405로_응답한다() throws Exception {
        mockMvc.perform(get("/test/exception/validated"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.code").value("COMMON-405"));
    }

    @Test
    void 지원하지_않는_미디어타입은_415로_응답한다() throws Exception {
        mockMvc.perform(post("/test/exception/validated")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.code").value("COMMON-415"));
    }

    @Test
    void 없는_경로는_404로_응답한다() throws Exception {
        mockMvc.perform(get("/test/exception/nowhere"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("COMMON-404"));
    }

    @Test
    void 지원하지_않는_메서드_응답은_Allow_헤더를_보존한다() throws Exception {
        mockMvc.perform(get("/test/exception/validated"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().exists("Allow"));
    }

    /**
     * 406 은 래퍼를 실을 수 없는 구조적 예외다.
     *
     * <p>클라이언트가 Accept 로 "JSON 은 안 받는다" 고 선언한 상황이라 어떤 HttpMessageConverter 도 우리 JSON 바디를 쓸 수 없다.
     * Content-Type 을 강제로 JSON 으로 덮어 밀어넣는 것은 406 의 의미와 모순되므로, 3xx·204 처럼 래퍼 예외로 둔다.
     */
    @Test
    void 받을_수_없는_미디어타입은_406_빈_본문으로_응답한다() throws Exception {
        String body = mockMvc.perform(post("/test/exception/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_PDF)
                        .content("{\"annualLeaveDays\": 3}"))
                .andExpect(status().isNotAcceptable())
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(body.isEmpty(), "406 응답 본문이 비어있지 않음: " + body);
    }

    @Test
    void 프레임워크_응답은_body_status와_HTTP_status가_일치한다() throws Exception {
        record Case(String label, org.springframework.test.web.servlet.RequestBuilder request) {}
        java.util.List<Case> cases = java.util.List.of(
                new Case("깨진 JSON", post("/test/exception/validated")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"annualLeaveDays\": ")),
                new Case("잘못된 메서드", get("/test/exception/validated")),
                new Case("미디어타입", post("/test/exception/validated")
                        .contentType(MediaType.TEXT_PLAIN).content("plain")),
                // 406 은 제외 — 본문 자체를 실을 수 없다 (위 테스트 참고).
                new Case("없는 경로", get("/test/exception/nowhere")));

        for (Case c : cases) {
            var response = mockMvc.perform(c.request()).andReturn().getResponse();
            String body = response.getContentAsString();
            org.junit.jupiter.api.Assertions.assertTrue(
                    body.contains("\"status\":" + response.getStatus()),
                    c.label() + " — HTTP " + response.getStatus() + " 와 body status 불일치: " + body);
        }
    }

    @Test
    void 프레임워크_4xx는_내부_정보를_노출하지_않는다() throws Exception {
        String body = mockMvc.perform(post("/test/exception/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"annualLeaveDays\": "))
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.junit.jupiter.api.Assertions.assertFalse(
                body.contains("JsonParseException") || body.contains("com.fasterxml"),
                "파서 예외 원문이 응답에 노출됨: " + body);
    }

    @TestConfiguration
    static class TestControllerConfig {

        @Bean
        ThrowingController throwingController() {
            return new ThrowingController();
        }
    }

    @RestController
    @RequestMapping("/test/exception")
    static class ThrowingController {

        @GetMapping("/domain")
        ApiResponseBody<Void> domain() {
            throw TestException.conflicted();
        }

        @GetMapping("/external")
        ApiResponseBody<Void> external() {
            throw TestException.externalFailed(new RuntimeException("read timeout"));
        }

        @PostMapping("/validated")
        ApiResponseBody<Void> validated(@Valid @RequestBody TestRequest request) {
            return ApiResponseBody.ok();
        }

        @GetMapping("/unexpected")
        ApiResponseBody<Void> unexpected() {
            throw new IllegalStateException("커넥션 풀이 고갈됨 — 내부 진단용 메시지");
        }
    }

    record TestRequest(@Min(value = 0, message = "0 이상이어야 합니다.") int annualLeaveDays) {}

    static final class TestException extends BaseException {

        private TestException(ErrorCode errorCode) {
            super(errorCode);
        }

        private TestException(ErrorCode errorCode, Throwable cause) {
            super(errorCode, cause);
        }

        static TestException conflicted() {
            return new TestException(TestErrorCode.CONFLICTED);
        }

        static TestException externalFailed(Throwable cause) {
            return new TestException(TestErrorCode.EXTERNAL_FAILED, cause);
        }
    }

    enum TestErrorCode implements ErrorCode {
        CONFLICTED("TEST-001", ErrorCategory.CONFLICT, "이미 처리된 요청입니다."),
        EXTERNAL_FAILED("TEST-002", ErrorCategory.EXTERNAL_API, "외부 서비스를 이용할 수 없습니다.");

        private final String code;
        private final ErrorCategory category;
        private final String message;

        TestErrorCode(String code, ErrorCategory category, String message) {
            this.code = code;
            this.category = category;
            this.message = message;
        }

        @Override
        public String code() {
            return code;
        }

        @Override
        public ErrorCategory category() {
            return category;
        }

        @Override
        public String message() {
            return message;
        }
    }
}
