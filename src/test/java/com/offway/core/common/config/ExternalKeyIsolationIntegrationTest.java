package com.offway.core.common.config;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 테스트가 실제 외부 API 를 때릴 수 있는 상태인지 감시한다.
 *
 * <p>있던 사고 — {@code application.properties} 가 {@code file:./application-secret.properties} 를 읽는데
 * Gradle 테스트 워커의 작업 디렉터리가 프로젝트 루트라 <b>테스트에도 실키가 주입됐다</b>. stub 을 빠뜨린
 * TMAP 경유지 최적화가 매 실행마다 실호출을 날려 일일 허용량을 갉아먹었다. 로그도 남지 않았다 — 호출이
 * 성공했으니 아무도 몰랐다.
 *
 * <p>이 테스트는 그 상태를 <b>깨지는 소리로</b> 바꾼다. stub 을 하나 빠뜨렸는지 일일이 쫓는 대신, 키가 없으면
 * 모든 클라이언트가 {@code hasKey()} 가드에서 폴백으로 떨어진다는 성질에 기댄다.
 *
 * <p>{@code -Pe2e} 로 실호출을 명시적으로 열었을 때만 비활성화된다.
 */
@SpringBootTest
@DisabledIfSystemProperty(named = "offway.e2e", matches = "true", disabledReason = "-Pe2e 는 실호출이 목적이다")
class ExternalKeyIsolationIntegrationTest {

    @Autowired
    private ExternalApiProperties properties;

    @Test
    void 테스트_컨텍스트에는_외부_API_키가_주입되지_않는다() {
        assertFalse(properties.tmap().hasKey(),
                "TMAP 키가 테스트에 주입됐습니다. 실호출로 일일 허용량이 샙니다 — build.gradle.kts 의 테스트 환경변수를 확인하세요.");
        assertFalse(properties.dataGoKr().hasKey(),
                "공공데이터 키가 테스트에 주입됐습니다. 실호출로 일일 허용량이 샙니다 — build.gradle.kts 의 테스트 환경변수를 확인하세요.");
    }
}
