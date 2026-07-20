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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
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
