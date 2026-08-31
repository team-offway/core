package com.offway.core.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 백오피스 화면의 진입 주소를 정한다(#343).
 *
 * <h2>왜 필요한가</h2>
 *
 * <p>Spring Boot 가 {@code index.html} 을 자동으로 찾아 주는 것은 <b>루트({@code /})뿐</b>이다. 하위
 * 경로는 정적 리소스 핸들러가 그대로 찾으므로 {@code /admin/} 은 디렉터리를 가리켜 404 가 된다.
 *
 * <p>그러면 사람이 {@code /admin/index.html} 을 통째로 외워 쳐야 하고, 로그인 콜백이 되돌려 보낼 주소도
 * 거기가 된다. 진입점이 파일 이름으로 드러나는 것은 나중에 화면 구조를 바꿀 때 발목을 잡는다.
 *
 * <p>그래서 {@code /admin}·{@code /admin/} 둘 다 실제 파일로 넘긴다. 슬래시 유무로 결과가 갈리면
 * "어제는 됐는데" 가 된다.
 *
 * <h2>redirect 가 아니라 forward 인 이유</h2>
 *
 * <p>로그인 결과는 <b>URL 프래그먼트</b>로 온다({@code /admin/#access_token=...}). 리다이렉트를 한 번 더
 * 태우면 브라우저가 프래그먼트를 새 주소에 옮겨 주긴 하지만 그 동작이 브라우저마다 미묘하게 달라, 토큰이
 * 조용히 사라지는 경로를 만들 이유가 없다. forward 는 주소가 그대로라 그런 여지가 없다.
 */
@Configuration
public class AdminConsoleWebConfig implements WebMvcConfigurer {

    /** 정적 SPA 의 실제 파일. {@code src/main/resources/static} 아래 경로다. */
    private static final String ADMIN_INDEX = "forward:/admin/index.html";

    private static final String ADMIN_PATH = "/admin";
    private static final String ADMIN_PATH_WITH_SLASH = "/admin/";

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController(ADMIN_PATH).setViewName(ADMIN_INDEX);
        registry.addViewController(ADMIN_PATH_WITH_SLASH).setViewName(ADMIN_INDEX);
    }
}
