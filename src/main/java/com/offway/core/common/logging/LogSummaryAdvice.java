package com.offway.core.common.logging;

import com.offway.core.common.response.ApiResponseBody;
import java.lang.reflect.Type;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 요청·응답 DTO 가 스스로 낸 요약을 걷어 요청 속성에 담는다. 출력은 {@link RequestLoggingFilter} 가 한다.
 *
 * <p><b>요청 본문을 진입 시점에 읽지 않는 이유</b> — 본문은 컨트롤러가 읽은 뒤에야 캐시되므로
 * {@code ContentCachingRequestWrapper} 를 써도 진입 시점의 {@code →} 줄에는 비어 있다. 진입에서 읽으려면
 * 스트림을 미리 소비해야 하고, 그러면 요청마다 본문을 두 번 들고 있게 된다. {@code →} 의 목적은
 * "요청이 시작됐다" 는 신호라 경로만으로 충분하다.
 *
 * <p>응답은 직렬화 <b>직전</b>에 걷는다. 그래서 직렬화가 broken pipe 로 깨져도 "무엇을 만들었는지" 는 남는다.
 */
@RestControllerAdvice
public class LogSummaryAdvice implements RequestBodyAdvice, ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public HttpInputMessage beforeBodyRead(
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return inputMessage;
    }

    @Override
    public Object afterBodyRead(
            Object body,
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        if (body instanceof LogSummary summary) {
            store(LogAttributes.REQUEST_SUMMARY, summary.logSummary());
        }
        return body;
    }

    @Override
    public Object handleEmptyBody(
            Object body,
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType contentType,
            Class<? extends HttpMessageConverter<?>> converterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (body instanceof ApiResponseBody<?> wrapper && wrapper.data() instanceof LogSummary summary) {
            store(LogAttributes.RESPONSE_SUMMARY, summary.logSummary());
        }
        return body;
    }

    private static void store(String key, String value) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            attributes.setAttribute(key, value, RequestAttributes.SCOPE_REQUEST);
        }
    }
}
