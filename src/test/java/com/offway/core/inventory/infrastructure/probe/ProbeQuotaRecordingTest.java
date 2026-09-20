package com.offway.core.inventory.infrastructure.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCallRecorder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 프로브가 자기가 태운 한도를 세는가(#594).
 *
 * <p><b>안 세던 시절이 있었다.</b> 프로브는 {@code externalWebClient} 를 바로 써서 기록기를 안 지났고,
 * 그래서 하루 288 콜이 {@code external_api_call} 에 한 줄도 안 남았다. 미상으로 세어지는 것보다 나쁘다 —
 * 미상은 "누군지 모르는 채 세어진 것" 이고 이쪽은 "세어지지도 않은 것" 이라, <b>앱은 한도가 남았다고
 * 판단해 배치를 발사한다.</b>
 *
 * <p>Spring·DB 없이 만든다. 세는 행위 자체는 여기서 잠그고, 저장까지 가는 경로는
 * {@code ExternalApiQuotaIntegrationTest} 가 실제 저장소로 본다.
 */
class ProbeQuotaRecordingTest {

    private static final String KEY = "TEST-KEY";

    /** 무엇을 몇 번 셌는지 붙잡는 기록기. */
    private static final class CountingRecorder extends ExternalApiCallRecorder {

        private final List<ExternalApi> recorded = new ArrayList<>();

        private CountingRecorder() {
            super(null, null);
        }

        @Override
        public void record(ExternalApi api) {
            recorded.add(api);
        }
    }

    private static ExternalApiProperties withKey() {
        return ExternalApiProperties.builder()
                .dataGoKr(new ExternalApiProperties.DataGoKr(KEY))
                .tmap(new ExternalApiProperties.Tmap(KEY))
                .build();
    }

    private static ExternalApiProperties withoutKey() {
        return ExternalApiProperties.builder()
                .dataGoKr(new ExternalApiProperties.DataGoKr(null))
                .tmap(new ExternalApiProperties.Tmap(null))
                .build();
    }

    private static WebClient answering(ClientResponse response) {
        return WebClient.builder().exchangeFunction(request -> Mono.just(response)).build();
    }

    private static WebClient ok() {
        return answering(ClientResponse.create(HttpStatus.OK).body("{}").build());
    }

    /**
     * 프로브 → 그 프로브가 깎는 한도. <b>여기가 기대값의 정본이다</b> — 프로덕션 코드의
     * {@code quota()} 를 그대로 불러 비교하면 아무것도 검증하지 않는다.
     */
    private static List<Expectation> expectations() {
        return List.of(
                new Expectation("TourApiProbe",
                        (r) -> new TourApiProbe(ok(), withKey(), r), Optional.of(ExternalApi.TOUR_API)),
                new Expectation("TourDataLabProbe",
                        (r) -> new TourDataLabProbe(ok(), withKey(), r), Optional.of(ExternalApi.TOUR_VISITOR)),
                new Expectation("HolidayProbe",
                        (r) -> new HolidayProbe(ok(), withKey(), r), Optional.of(ExternalApi.HOLIDAY)),
                new Expectation("TagoProbe",
                        (r) -> new TagoProbe(ok(), withKey(), r), Optional.of(ExternalApi.BUS_ARRIVAL)),
                new Expectation("TmapProbe",
                        (r) -> new TmapProbe(ok(), withKey(), r), Optional.of(ExternalApi.TMAP_ROUTE)),
                // 코레일은 앱이 안 부르는 API 라 ExternalApi 에 항목이 없다 — 셀 자리가 없다.
                new Expectation("KorailProbe",
                        (r) -> new KorailProbe(ok(), withKey(), r), Optional.empty()));
    }

    private record Expectation(
            String name, Function<ExternalApiCallRecorder, ExternalApiProbe> create, Optional<ExternalApi> quota) {}

    /**
     * <b>새 프로브가 생기면 이 테스트가 먼저 깨진다.</b>
     *
     * <p>아래 표는 손으로 적는다. 그러면 프로브를 추가한 사람이 여기 안 적어도 초록이 되는데, 그게
     * 바로 이 이슈가 고치려는 모양이다 — <b>빠뜨려도 아무것도 안 깨지는 상태.</b> 그래서 개수를 맞춰
     * 둔다.
     */
    @Test
    void 프로브를_추가하면_이_표에도_적는다() throws java.io.IOException {
        java.nio.file.Path dir =
                java.nio.file.Path.of("src", "main", "java", "com", "offway", "core",
                        "inventory", "infrastructure", "probe");
        try (var files = java.nio.file.Files.list(dir)) {
            List<String> implementations = files
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith("Probe.java"))
                    // 인터페이스와 공통 상위는 프로브 자체가 아니다.
                    .filter(name -> !name.equals("ExternalApiProbe.java"))
                    .filter(name -> !name.equals("AbstractDataGoKrProbe.java"))
                    .sorted()
                    .toList();

            assertEquals(implementations.size(), expectations().size(),
                    "프로브 구현과 기대표의 개수가 다르다 — 새 프로브의 한도를 적지 않았다: " + implementations);
        }
    }

    @Test
    void 프로브마다_자기가_깎는_한도를_한_번_센다() {
        for (Expectation expectation : expectations()) {
            CountingRecorder recorder = new CountingRecorder();

            expectation.create().apply(recorder).probe();

            assertEquals(expectation.quota().map(List::of).orElseGet(List::of), recorder.recorded,
                    expectation.name() + " 가 센 한도가 기대와 다르다");
        }
    }

    /**
     * <b>TMAP 은 경로(1,000)지 경유지최적화(50)가 아니다.</b>
     *
     * <p>섞으면 프로브 48 콜이 하루 50 짜리 한도를 거의 다 먹어 <b>그날 코스 동선을 못 짠다.</b>
     * 같은 TMAP 이라고 한 항목으로 묶지 않는 이유라, 따로 못 박아 둔다.
     */
    @Test
    void TMAP_프로브는_경유지최적화_한도를_건드리지_않는다() {
        CountingRecorder recorder = new CountingRecorder();

        new TmapProbe(ok(), withKey(), recorder).probe();

        assertEquals(List.of(ExternalApi.TMAP_ROUTE), recorder.recorded);
    }

    /**
     * <b>키가 없으면 세지 않는다.</b>
     *
     * <p>호출이 아예 안 나가므로 한도도 안 깎인다. 여기서 세면 로컬·CI 처럼 키 없이 뜨는 환경이
     * 운영 사용량을 부풀린다 — 그 숫자로 "한도가 빠듯하다" 는 판단을 하게 된다.
     */
    @Test
    void 키가_없으면_호출도_집계도_없다() {
        for (Expectation expectation : expectations()) {
            CountingRecorder recorder = new CountingRecorder();
            ExternalApiProbe probe = switch (expectation.name()) {
                case "TourApiProbe" -> new TourApiProbe(ok(), withoutKey(), recorder);
                case "TourDataLabProbe" -> new TourDataLabProbe(ok(), withoutKey(), recorder);
                case "HolidayProbe" -> new HolidayProbe(ok(), withoutKey(), recorder);
                case "TagoProbe" -> new TagoProbe(ok(), withoutKey(), recorder);
                case "TmapProbe" -> new TmapProbe(ok(), withoutKey(), recorder);
                case "KorailProbe" -> new KorailProbe(ok(), withoutKey(), recorder);
                default -> throw new IllegalStateException("새 프로브가 생겼다: " + expectation.name());
            };

            ProbeResult result = probe.probe();

            assertTrue(recorder.recorded.isEmpty(), expectation.name() + " 가 호출도 없이 한도를 셌다");
            assertEquals(ProbeResult.Status.SKIPPED_NO_KEY, result.status(),
                    expectation.name() + " 가 키 없이 SKIPPED 가 아니다");
        }
    }

    /**
     * <b>실패해도 센다.</b>
     *
     * <p>게이트웨이가 4xx·5xx 로 답해도 그 호출은 이미 나갔고 한도는 깎였다(#123). 성공만 세면 키가
     * 만료된 날 사용량이 0 으로 보이는데, 정작 그때가 남은 한도를 정확히 알아야 하는 순간이다.
     */
    @Test
    void 응답이_실패여도_센다() {
        CountingRecorder recorder = new CountingRecorder();
        WebClient failing = answering(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).body("").build());

        new TourApiProbe(failing, withKey(), recorder).probe();

        assertEquals(List.of(ExternalApi.TOUR_API), recorder.recorded);
    }
}
