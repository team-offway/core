package com.offway.core.user.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.offway.core.user.domain.AuthProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * 환경변수에 콤마로 나열한 audience 가 <b>여러 값으로 읽히는가</b>(#34).
 *
 * <p>한 provider 가 audience 를 여럿 가져야 하는 것은 선택이 아니다. Apple 은 네이티브 로그인이 앱 Bundle ID 를,
 * 웹·Android 가 Service ID 를 {@code aud} 에 담고 <b>둘이 다른 값</b>이다. Google 도 앱이 serverClientID 를
 * 지정했는지에 따라 웹·iOS 클라이언트 ID 로 갈린다. 어느 쪽을 빼면 그 경로의 로그인이 <b>전부</b> 401 이 된다.
 *
 * <p><b>이것이 조용히 깨질 수 있어서 잠근다.</b> 콤마 문자열이 목록으로 안 쪼개지면 {@code aud} 비교가 그
 * 통짜 문자열과 이뤄져 어떤 토큰도 통과하지 못한다 — 설정은 채워져 있고 부팅도 되므로 원인이 안 드러난다.
 */
class AuthPropertiesBindingTest {

    private static final String SERVICE_ID = "com.nth.offway.service";
    private static final String BUNDLE_ID = "com.nth.offway";

    private static AuthProperties bind(String appleAudiences) {
        MapConfigurationPropertySource source =
                new MapConfigurationPropertySource(Map.of("offway.auth.oidc.apple.audiences", appleAudiences));
        return new Binder(source).bind("offway.auth", AuthProperties.class).get();
    }

    @Test
    void 콤마로_나열하면_각각의_audience_가_된다() {
        assertEquals(List.of(SERVICE_ID, BUNDLE_ID), bind(SERVICE_ID + "," + BUNDLE_ID).audiencesOf(AuthProvider.APPLE));
    }

    @ParameterizedTest
    @ValueSource(strings = {",", ", ", " , "})
    void 구분자_주변_공백은_값에_섞이지_않는다(String delimiter) {
        // 시크릿을 손으로 넣다 보면 공백이 들어간다. 값에 섞이면 aud 비교가 어긋나 로그인이 전부 막힌다.
        assertEquals(
                List.of(SERVICE_ID, BUNDLE_ID), bind(SERVICE_ID + delimiter + BUNDLE_ID).audiencesOf(AuthProvider.APPLE));
    }

    @Test
    void 하나만_넣어도_그대로_읽힌다() {
        assertEquals(List.of(SERVICE_ID), bind(SERVICE_ID).audiencesOf(AuthProvider.APPLE));
    }

    @Test
    void 설정이_없는_provider_는_빈_목록이다() {
        // 빈 목록이면 그 provider 로그인을 아예 받지 않는다 — 남의 앱 토큰을 받아주느니 닫는 쪽이다.
        assertEquals(List.of(), bind(SERVICE_ID).audiencesOf(AuthProvider.GOOGLE));
    }
}
