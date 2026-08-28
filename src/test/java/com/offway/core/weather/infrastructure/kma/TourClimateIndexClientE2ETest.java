package com.offway.core.weather.infrastructure.kma;

import com.offway.core.common.external.NoOpCallRecorder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.weather.domain.SigunguKey;
import com.offway.core.weather.domain.TourClimateGrade;
import com.offway.core.weather.domain.TourClimateIndex;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 기상청 관광기후지수 실제 외부 호출 E2E(#130) — stub 없이 직접 부른다. CI 기본 실행에서 제외한다({@code
 * DATA_GO_KR_SERVICE_KEY} 있을 때만).
 *
 * <p><b>이 테스트의 핵심은 매칭이다.</b> 접점(base·파라미터·필드명)이 멀쩡해도 시군구명 표기가 어긋나면 우리 89개 지역이
 * 조용히 빈다. 응답의 {@code cityAreaId} 가 법정동코드가 아니라 코드 조인이 안 되므로(89곳 중 51곳만 일치), 이름 매칭이
 * 유일한 통로이고 그래서 여기서 고정한다.
 */
@EnabledIfEnvironmentVariable(named = "DATA_GO_KR_SERVICE_KEY", matches = ".+")
class TourClimateIndexClientE2ETest {

    private static final ZoneId KMA_ZONE = ZoneId.of("Asia/Seoul");

    /** 실호출 확인(2026-08-03) 236곳. 이보다 크게 줄면 페이지 크기·스키마가 바뀐 신호다. */
    private static final int EXPECTED_SIGUNGU_COUNT = 200;

    /**
     * 우리 89곳 중 매칭이 깨지기 쉬운 표본. 특별자치도 전환(강원·전북), 비표준 코드로 묶인 광주·전남, 동명이의(고성군)를
     * 모두 포함한다.
     */
    private static final List<SigunguKey> SAMPLE = List.of(
            SigunguKey.of("강원특별자치도", "정선군"),
            SigunguKey.of("강원특별자치도", "태백시"),
            SigunguKey.of("강원특별자치도", "고성군"),
            SigunguKey.of("경상남도", "고성군"),
            SigunguKey.of("전북특별자치도", "무주군"),
            SigunguKey.of("전남광주통합특별시", "완도군"),
            SigunguKey.of("전남광주통합특별시", "담양군"),
            SigunguKey.of("부산광역시", "동구"),
            SigunguKey.of("충청북도", "단양군"),
            SigunguKey.of("경상북도", "영양군"));

    private static TourClimateIndexClient client() {
        ExternalApiProperties props = new ExternalApiProperties(
                new ExternalApiProperties.DataGoKr(System.getenv("DATA_GO_KR_SERVICE_KEY")), null);
        return new TourClimateIndexClientImpl(WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build(), props, new NoOpCallRecorder());
    }

    @Test
    void 한_번_호출로_전_기간이_온다() {
        // DAY 는 오프셋이 아니라 며칠치다. 날짜마다 부르면 같은 데이터를 아홉 번 받는다.
        Map<LocalDate, Map<SigunguKey, TourClimateIndex>> forecast = client().forecast();

        int expectedDays = TourClimateIndex.LAST_DAY - TourClimateIndex.FIRST_DAY + 1;
        assertEquals(expectedDays, forecast.size(),
                "받은 날짜 수가 다릅니다 — DAY 의 의미나 조회 범위가 바뀌었습니다. 실제=" + forecast.keySet());
    }

    @Test
    void 내일부터_아흐레_뒤까지_덮는다() {
        LocalDate today = LocalDate.now(KMA_ZONE);
        Map<LocalDate, Map<SigunguKey, TourClimateIndex>> forecast = client().forecast();

        assertNotNull(forecast.get(today.plusDays(TourClimateIndex.FIRST_DAY)), "D+1 이 비었습니다");
        assertNotNull(forecast.get(today.plusDays(TourClimateIndex.LAST_DAY)), "D+9 가 비었습니다");
        assertEquals(null, forecast.get(today), "오늘은 조회 대상이 아니다");
    }

    @Test
    void 날짜마다_전국_시군구가_다_온다() {
        // numOfRows 가 모자라면 뒷 날짜가 통째로 잘린다 — 응답이 날짜순이라 부족분이 특정 날짜에 몰린다.
        Map<LocalDate, Map<SigunguKey, TourClimateIndex>> forecast = client().forecast();

        forecast.forEach((date, byKey) -> assertTrue(byKey.size() >= EXPECTED_SIGUNGU_COUNT,
                date + " 의 시군구가 " + byKey.size() + "곳뿐입니다 — numOfRows 가 모자랍니다"));
    }

    @Test
    void 매칭이_까다로운_지역들이_모두_잡힌다() {
        // 여기가 깨지면 그 지역만 조용히 지수가 빈다. 부가 정보라 아무도 눈치채지 못한다.
        LocalDate target = LocalDate.now(KMA_ZONE).plusDays(TourClimateIndex.FIRST_DAY);
        Map<SigunguKey, TourClimateIndex> byKey = client().forecast().get(target);

        for (SigunguKey key : SAMPLE) {
            assertNotNull(byKey.get(key), key + " 를 못 찾았습니다 — 시도·시군구명 표기가 바뀌었습니다");
        }
    }

    @Test
    void 등급을_아는_값으로_읽는다() {
        long unknown = client().forecast().values().stream()
                .flatMap(byKey -> byKey.values().stream())
                .filter(index -> index.grade() == TourClimateGrade.UNKNOWN)
                .count();

        assertEquals(0, unknown, "모르는 등급 문구가 " + unknown + "건 — 등급 체계가 바뀌었습니다");
    }
}
