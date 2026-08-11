package com.offway.core.weather.infrastructure.airkorea;

import com.offway.core.common.external.NoOpCallRecorder;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.weather.domain.AirGrade;
import com.offway.core.weather.domain.AirQuality;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 에어코리아 대기질 실제 외부 호출 E2E — stub 없이 직접 부른다. CI 기본 실행에서 제외한다({@code
 * DATA_GO_KR_SERVICE_KEY} 있을 때만).
 *
 * <p><b>이 테스트가 없어서 인증키 재발급 때 대기질이 조용히 죽을 뻔했다</b>(#165). 같은 버그를 가진 열차 어댑터는
 * E2E 가 있어 바로 드러났는데, 대기질은 확인할 그물이 없었다. 대기질은 부가 정보라 화면에서도 티가 안 난다.
 *
 * <p>확인 대상은 base·파라미터명·응답 필드명, 그리고 <b>인코딩</b>이다 — 한글 시도명은 인코딩돼야 하고 serviceKey 는
 * 다시 인코딩되면 안 된다. 둘을 한 URI 에서 만족시켜야 해서 깨지기 쉬운 자리다.
 */
@EnabledIfEnvironmentVariable(named = "DATA_GO_KR_SERVICE_KEY", matches = ".+")
class AirKoreaClientE2ETest {

    private static AirKoreaClient client() {
        ExternalApiProperties props = new ExternalApiProperties(
                new ExternalApiProperties.DataGoKr(System.getenv("DATA_GO_KR_SERVICE_KEY")), null);
        return new AirKoreaClientImpl(WebClient.builder().build(), props, new NoOpCallRecorder());
    }

    @ParameterizedTest(name = "{0} 대기질을 실제로 받아온다")
    @ValueSource(strings = {"서울", "부산", "강원", "전남"})
    void 한글_시도명으로_대기질을_받아온다(String sido) {
        // 시도명이 한글이라 인코딩이 필요한데, 그 김에 serviceKey 까지 재인코딩하면 키가 달라져 전부 실패한다(#165).
        AirQuality air = client().realtimeBySido(sido)
                .orElseThrow(() -> new AssertionError(sido + " 대기질이 비었습니다 — 인코딩·파라미터명·응답 스키마를 의심하세요"));

        assertNotNull(air.pm10(), "미세먼지 평균이 없습니다 — pm10Value 필드명이 바뀌었을 수 있습니다");
        assertNotEquals(AirGrade.UNKNOWN, air.grade(), "통합등급을 못 읽었습니다 — khaiGrade 를 확인하세요");
    }
}
