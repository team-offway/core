package com.offway.core.user.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.offway.core.common.logging.LogAttributes;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.ObjectMapper;

/**
 * 401 이 남기는 흔적(#41).
 *
 * <p><b>이 줄이 그 요청에 대해 남는 전부다.</b> 401 은 요청 로깅 필터보다 앞선 보안 필터에서 끝나 요청 줄이
 * 찍히지 않는다 — 여기에 신원 단서가 없으면 "누가 401 을 맞고 있나" 에 답할 수단이 아예 없다.
 *
 * <p>Spring 컨텍스트를 띄우지 않는다. 요청 하나를 받아 로그 한 줄과 응답을 만드는 순수한 변환이라 서블릿
 * 목만으로 전부 검증된다.
 */
class ApiResponseAuthenticationEntryPointTest {

    private final ApiResponseAuthenticationEntryPoint entryPoint =
            new ApiResponseAuthenticationEntryPoint(new ObjectMapper());

    @Test
    void 토큰을_왜_거절했는지_같은_줄에_남긴다() throws IOException {
        // 만료(앱이 재발급하면 끝)와 위조(우리 키로 서명되지 않음)는 대응이 정반대다. 사유 칸이 없으면
        // 둘이 같은 401 로 뭉쳐 어느 쪽이 늘고 있는지 알 수 없다.
        MockHttpServletRequest request = apiRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer some.jwt.value");
        request.setAttribute(LogAttributes.TOKEN_REJECTION, "JwtValidationException");
        request.setRemoteAddr("203.0.113.7");

        String logged = commenceAndCapture(request);

        assertTrue(logged.contains("scheme=bearer"), logged);
        assertTrue(logged.contains("reason=JwtValidationException"), logged);
        assertTrue(logged.contains("ip=203.0.113.7"), logged);
    }

    @Test
    void 자격증명을_안_들고_온_요청은_수단이_none_이다() throws IOException {
        // 스캐너와 "앱이 토큰을 들고 왔는데 거절당함" 을 가르는 칸이다. 둘이 섞이면 401 건수가 늘어도
        // 앱 문제인지 소음인지 판단할 수 없다.
        assertTrue(commenceAndCapture(apiRequest()).contains("scheme=none"));
    }

    @Test
    void 자격증명_자체는_절대_남기지_않는다() throws IOException {
        MockHttpServletRequest request = apiRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer super.secret.token");

        assertFalse(commenceAndCapture(request).contains("super.secret.token"));
    }

    @Test
    void 우리_API_가_아닌_401_은_info_로_남기지_않는다() throws IOException {
        // 공인 IP 에 붙은 서버라 /wp-admin 같은 스캐너가 쉬지 않고 두드린다. info 로 두면 정작 봐야 할
        // 사용자 요청 로그가 그 사이에 묻힌다.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/wp-admin/setup-config.php");

        assertEquals("", commenceAndCapture(request));
    }

    private MockHttpServletRequest apiRequest() {
        return new MockHttpServletRequest("GET", "/api/v1/courses");
    }

    /** 엔트리 포인트를 한 번 태우고 그때 남은 info 로그를 합쳐 돌려준다. */
    private String commenceAndCapture(MockHttpServletRequest request) throws IOException {
        Logger logger = (Logger) LoggerFactory.getLogger(ApiResponseAuthenticationEntryPoint.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            entryPoint.commence(request, new MockHttpServletResponse(), new BadCredentialsException("테스트"));
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list.stream()
                .filter(event -> event.getLevel() == Level.INFO)
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", String::concat);
    }
}
