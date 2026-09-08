package com.offway.core.trip.infrastructure.festival;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.external.NoOpCallRecorder;
import com.offway.core.trip.infrastructure.festival.dto.StandardFestival;
import com.offway.core.trip.infrastructure.festival.dto.StandardFestivalResult;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 전국문화축제표준데이터를 <b>실제로 부른다</b>(#506).
 *
 * <h2>왜 이 테스트가 필요한가</h2>
 *
 * <p>이 어댑터는 두 번 조용히 틀렸다. 한 번은 <b>신청할 수 없는 오픈API</b> 를 불렀고(403 이 영영
 * 안 풀린다), 또 한 번은 <b>필드명을 추측</b>했다({@code fstvlNm} vs 실제 {@code FSTVL_NM}). 둘 다
 * 통합 테스트는 초록이었다 — stub 이 우리가 기대하는 모양을 그대로 돌려주기 때문이다.
 *
 * <p>그래서 <b>실제 외부와 우리 코드의 접점</b>만 보는 테스트를 따로 둔다. 주소·파라미터·필드명이
 * 하나라도 어긋나면 여기서 깨진다.
 *
 * <h2>인증키를 쓰지 않는다</h2>
 *
 * <p>파일 주소라 키가 필요 없다. 다만 <b>실호출이라 네트워크에 기댄다</b> — 규약대로 환경변수가
 * 있을 때만 돈다(CI 기본 실행에서 제외).
 *
 * <pre>{@code
 *   E2E_EXTERNAL=1 ./gradlew test --tests '*FestivalStandardClientE2ETest*'
 * }</pre>
 */
@EnabledIfEnvironmentVariable(named = "E2E_EXTERNAL", matches = ".+")
class FestivalStandardClientE2ETest {

    /** 응답이 1MB 를 넘봐서 기본 상한(256KB)으로는 못 받는다 — 운영은 공용 WebClient 가 2MB 다. */
    private static final int MAX_IN_MEMORY = 4 * 1024 * 1024;

    /** 실측 1,305건. 원본이 줄 수는 있어도 이 아래로 떨어지면 무언가 잘못된 것이다. */
    private static final int MIN_EXPECTED = 500;

    private static FestivalStandardClientImpl client() {
        WebClient webClient = WebClient.builder()
                .codecs((ClientCodecConfigurer codecs) ->
                        codecs.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY))
                .build();
        return new FestivalStandardClientImpl(webClient, new NoOpCallRecorder());
    }

    /**
     * <b>키 없이 전량이 온다.</b>
     *
     * <p>이 단언이 곧 "신청이 필요 없다" 는 증거다. 예전 주소는 여기서 403 으로 깨졌을 것이다.
     */
    @Test
    void 인증키_없이_전국_축제를_받는다() {
        StandardFestivalResult result = client().findAll(Duration.ofSeconds(60));

        assertTrue(result.totalCount() > MIN_EXPECTED,
                "받은 행이 " + result.totalCount() + "건뿐이다 — 주소나 파라미터가 바뀌었는지 확인하라");
        assertFalse(result.items().isEmpty(), "쓸 수 있는 축제가 하나도 없다");
    }

    /**
     * <b>필드가 실제로 채워진다.</b>
     *
     * <p>필드명이 틀리면 이름을 못 읽어 어댑터가 던지므로 위 테스트에서 이미 걸린다. 여기서는 그
     * 다음 단계 — 기간·좌표·주소가 제자리에 오는지를 본다. 좌표가 없으면 동선에 못 올린다.
     */
    @Test
    void 축제에_기간과_좌표가_실려_온다() {
        StandardFestival festival = client().findAll(Duration.ofSeconds(60)).items().get(0);

        assertNotNull(festival.name());
        assertNotNull(festival.eventStart(), "기간이 없으면 여행일과 맞출 수 없다");
        assertNotNull(festival.eventEnd());
        assertNotNull(festival.lat(), "좌표가 없으면 동선에 못 올린다");
        assertNotNull(festival.lng());
        assertNotNull(festival.sigunguName(), "지역 매칭은 주소에서 뽑은 시군구로 한다");
    }

    /**
     * <b>우리 89곳이 실제로 섞여 있다.</b>
     *
     * <p>전국 데이터를 받아도 인구감소지역이 없으면 코스에 아무것도 안 붙는다. 실측(2026-09-07)으로
     * 73곳에 392건이었다 — 여기서는 그중 몇 곳만 표본으로 본다.
     */
    @Test
    void 우리_지역_축제가_섞여_있다() {
        Set<String> sigungus = client().findAll(Duration.ofSeconds(60)).items().stream()
                .map(StandardFestival::sigunguName)
                .collect(Collectors.toSet());

        // 실측에서 축제가 많았던 곳들. 전부는 아니어도 하나는 있어야 한다.
        assertTrue(sigungus.stream().anyMatch(Set.of("신안군", "보령시", "하동군", "영월군")::contains),
                "우리 89곳 중 축제가 많은 지역이 하나도 없다 — 주소 파싱을 확인하라: " + sigungus.size() + "개 시군구");
    }
}
