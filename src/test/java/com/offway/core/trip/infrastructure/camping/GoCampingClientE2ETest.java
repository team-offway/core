package com.offway.core.trip.infrastructure.camping;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.config.ExternalApiProperties.DataGoKr;
import com.offway.core.common.config.ExternalApiProperties.Tmap;
import com.offway.core.common.external.NoOpCallRecorder;
import com.offway.core.trip.infrastructure.camping.dto.GoCampsite;
import com.offway.core.trip.infrastructure.camping.dto.GoCampsiteResult;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 고캠핑을 <b>실제로 부른다</b>(#510).
 *
 * <h2>왜 이 테스트가 필요한가</h2>
 *
 * <p>같은 계열 어댑터가 두 번 조용히 틀렸다. 한 번은 <b>신청할 수 없는 오픈API</b> 를 불렀고, 또 한 번은
 * <b>필드명을 추측</b>했다({@code fstvlNm} vs 실제 {@code FSTVL_NM}). 둘 다 통합 테스트는 초록이었다 —
 * stub 이 우리가 기대하는 모양을 그대로 돌려주기 때문이다.
 *
 * <p>그래서 <b>실제 외부와 우리 코드의 접점</b>만 보는 테스트를 따로 둔다. 주소·파라미터·필드명이
 * 하나라도 어긋나면 여기서 깨진다.
 *
 * <h2>키가 필요하다</h2>
 *
 * <p>축제(파일 주소)와 달리 인증키를 쓴다. 키를 환경변수로 받고, 없으면 아예 돌지 않는다 —
 * 규약대로 격리해 CI 기본 실행에서 제외한다.
 *
 * <pre>{@code
 *   E2E_EXTERNAL=1 DATA_GO_KR_SERVICE_KEY='<인코딩된 키>' \
 *     ./gradlew test -Pe2e --tests '*GoCampingClientE2ETest*'
 * }</pre>
 *
 * <p><b>{@code -Pe2e} 를 빠뜨리면 조용히 건너뛴다.</b> 빌드 스크립트가 그 플래그 없이는 키 환경변수를
 * <b>빈 문자열로 덮기</b> 때문이다(실호출을 실수로 켜지 않으려는 방어선). 그러면 아래 조건이 거짓이 돼
 * 세 테스트가 전부 skip 되는데, 콘솔에는 {@code BUILD SUCCESSFUL} 만 뜬다 — 실제로 한 번 그렇게
 * "통과" 를 봤다. 돌았는지는 {@code build/test-results} 의 {@code skipped} 로 확인한다.
 *
 * <p><b>키는 이미 URL 인코딩된 값이다.</b> 다시 인코딩하면 {@code %2B} 가 {@code %252B} 가 돼
 * "등록되지 않은 서비스키"(30) 로 거절당한다 — 실제로 그렇게 한 번 403 을 봤다.
 */
@EnabledIfEnvironmentVariable(named = "E2E_EXTERNAL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DATA_GO_KR_SERVICE_KEY", matches = ".+")
class GoCampingClientE2ETest {

    /** 응답이 7.3MB 라 기본 상한(256KB)으로는 못 받는다 — 어댑터가 스스로 올린다. */
    private static final Duration WAIT = Duration.ofSeconds(90);

    /** 실측 3,115건 중 운영 중이 2,992건. 원본이 줄 수는 있어도 이 아래면 무언가 잘못된 것이다. */
    private static final int MIN_EXPECTED = 1_500;

    private static GoCampingClientImpl client() {
        ExternalApiProperties props = new ExternalApiProperties(
                new DataGoKr(System.getenv("DATA_GO_KR_SERVICE_KEY")), new Tmap(null));
        return new GoCampingClientImpl(WebClient.builder().build(), props, new NoOpCallRecorder());
    }

    /**
     * <b>전량이 한 번에 온다.</b>
     *
     * <p>이 단언이 곧 "페이지를 나눌 필요가 없다" 는 근거다. 원본이 늘어 한 요청에 안 담기기 시작하면
     * 여기서 걸린다.
     */
    @Test
    void 전국_야영장을_한_번에_받는다() {
        GoCampsiteResult result = client().findAll(WAIT);

        assertTrue(result.totalCount() > MIN_EXPECTED,
                "외부가 말한 전체가 " + result.totalCount() + "건뿐이다 — 주소나 파라미터를 확인하라");
        assertTrue(result.items().size() > MIN_EXPECTED,
                "쓸 수 있는 야영장이 " + result.items().size() + "건뿐이다 — 필드명이나 좌표 판정을 확인하라");
    }

    /**
     * <b>필드가 실제로 채워진다.</b>
     *
     * <p>이름을 못 읽으면 어댑터가 던지므로 위에서 이미 걸린다. 여기서는 그다음 단계 — 좌표·주소·사진이
     * 제자리에 오는지를 본다. 좌표가 없으면 동선에 못 올리고, 사진이 없으면 이 소스를 들여온 이유가
     * 사라진다.
     */
    @Test
    void 좌표와_사진이_실려_온다() {
        GoCampsiteResult result = client().findAll(WAIT);

        GoCampsite first = result.items().get(0);
        assertNotNull(first.externalId(), "자연키가 없으면 재적재마다 행이 쌓인다");
        assertNotNull(first.name());
        assertNotNull(first.address(), "지역 매칭은 주소로 한다");
        assertNotNull(first.lat(), "좌표가 없으면 동선에 못 올린다");
        assertNotNull(first.lng());

        // 실측 75%. 절반 아래로 떨어지면 원본이 사진을 빼기 시작한 것이라 이 소스의 값어치가 달라진다.
        long withPhoto = result.items().stream()
                .filter(campsite -> campsite.imageUrl() != null)
                .count();
        assertTrue(withPhoto * 2 > result.items().size(),
                "사진 보유율이 절반 아래다(" + withPhoto + "/" + result.items().size() + ") — 이 소스를 쓰는 근거를 다시 보라");
    }

    /**
     * <b>우리 89곳이 실제로 섞여 있다.</b>
     *
     * <p>전국 데이터를 받아도 인구감소지역이 없으면 코스에 아무것도 안 붙는다. 실측(2026-09-08)으로
     * 87곳에 1,698건이었다 — 여기서는 그중 몇 곳만 표본으로 본다.
     */
    @Test
    void 우리_지역_야영장이_섞여_있다() {
        Set<String> sigungus = client().findAll(WAIT).items().stream()
                .map(GoCampsite::sigunguName)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        assertFalse(sigungus.isEmpty(), "시군구명을 하나도 못 읽었다 — 필드명을 확인하라");
        assertTrue(sigungus.stream().anyMatch(Set.of("정선군", "남해군", "영월군", "고성군")::contains),
                "우리 89곳 중 야영장이 많은 지역이 하나도 없다: " + sigungus.size() + "개 시군구");
    }
}
