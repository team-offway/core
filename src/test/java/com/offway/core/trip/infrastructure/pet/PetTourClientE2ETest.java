package com.offway.core.trip.infrastructure.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.config.ExternalApiProperties.DataGoKr;
import com.offway.core.common.config.ExternalApiProperties.Tmap;
import com.offway.core.common.external.NoOpCallRecorder;
import com.offway.core.trip.infrastructure.pet.dto.PetTourDetail;
import com.offway.core.trip.infrastructure.pet.dto.PetTourPlace;
import com.offway.core.trip.infrastructure.pet.dto.PetTourResult;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 반려동물 동반여행을 <b>실제로 부른다</b>(#566).
 *
 * <h2>왜 이 테스트가 필요한가</h2>
 *
 * <p>같은 계열 어댑터가 <b>필드명을 추측했다가 틀린</b> 적이 있다({@code fstvlNm} vs 실제
 * {@code FSTVL_NM}, #506). 통합 테스트는 stub 이 기대한 모양을 그대로 돌려주므로 초록이었고 운영에서
 * 0건이 됐다.
 *
 * <p>이 API 는 위험이 하나 더 있다 — <b>목록과 상세의 필드 표기가 다르다.</b> 목록은 소문자
 * ({@code contentid}), 상세는 카멜케이스({@code acmpyTypeCd})다. 한쪽 표기로 통일해 읽으면 조용히
 * 0건이 된다.
 *
 * <h2>키가 필요하다</h2>
 *
 * <pre>{@code
 *   E2E_EXTERNAL=1 DATA_GO_KR_SERVICE_KEY='<인코딩된 키>' \
 *     ./gradlew test -Pe2e --tests '*PetTourClientE2ETest*'
 * }</pre>
 *
 * <p><b>{@code -Pe2e} 를 빠뜨리면 조용히 건너뛴다.</b> 빌드 스크립트가 그 플래그 없이는 키 환경변수를
 * 빈 문자열로 덮기 때문이다. 콘솔에는 {@code BUILD SUCCESSFUL} 만 뜨므로, 돌았는지는
 * {@code build/test-results} 의 {@code skipped} 로 확인한다.
 *
 * <p><b>키는 이미 URL 인코딩된 값이다.</b> 다시 인코딩하면 {@code %2B} 가 {@code %252B} 가 돼
 * "등록되지 않은 서비스키"(30) 로 거절당한다.
 */
@EnabledIfEnvironmentVariable(named = "E2E_EXTERNAL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DATA_GO_KR_SERVICE_KEY", matches = ".+")
class PetTourClientE2ETest {

    /** 응답이 6.3MB 라 기본 상한으로는 못 받는다 — 어댑터가 스스로 올린다. */
    private static final Duration WAIT = Duration.ofSeconds(90);

    /** 실측 전국 9,679건(2026-09-13). 원본이 줄 수는 있어도 이 아래면 무언가 잘못된 것이다. */
    private static final int MIN_EXPECTED = 5_000;

    private static PetTourClientImpl client() {
        ExternalApiProperties props = ExternalApiProperties.ofDataGoKr(System.getenv("DATA_GO_KR_SERVICE_KEY"));
        return new PetTourClientImpl(WebClient.builder().build(), props, new NoOpCallRecorder());
    }

    /**
     * <b>지역 파라미터 없이 전량이 한 번에 온다.</b>
     *
     * <p>이 단언이 곧 "89콜이 아니라 1콜이면 된다" 는 근거다. 원본이 늘어 한 요청에 안 담기기
     * 시작하면 어댑터가 잘림을 판정해 던지고, 여기서 걸린다.
     */
    @Test
    void 전국_반려동반_장소를_한_번에_받는다() {
        PetTourResult result = client().findAll(WAIT);

        assertTrue(result.totalCount() > MIN_EXPECTED,
                "외부가 말한 전체가 " + result.totalCount() + "건뿐이다 — 주소나 파라미터를 확인하라");
        assertTrue(result.places().size() > MIN_EXPECTED,
                "매칭할 수 있는 장소가 " + result.places().size() + "건뿐이다 — 목록 필드명을 확인하라");
    }

    /**
     * <b>법정동 코드가 실려 온다</b> — 이 값이 우리 89곳 매칭의 근거다.
     *
     * <p>이것이 비면 지역별로 89번 호출하는 설계로 돌아가야 하고, 매칭도 TourAPI 지역코드 변환을
     * 거쳐 누락이 생긴다(#555 실측에서 170 vs 442건 차이).
     */
    @Test
    void 법정동_코드가_실려_온다() {
        PetTourResult result = client().findAll(WAIT);

        PetTourPlace first = result.places().get(0);
        assertNotNull(first.contentId(), "이 값이 장소 풀 매칭 키다");
        assertNotNull(first.title());
        assertNotNull(first.legalCode(), "법정동 코드가 없으면 우리 89곳과 맞출 수 없다");
        assertEquals5Digits(first.legalCode());

        // 실측 9,675 / 9,679. 대부분이 비면 이 설계의 전제가 깨진다.
        long withCode = result.places().stream().filter(place -> place.legalCode() != null).count();
        assertTrue(withCode * 2 > result.places().size(),
                "법정동 코드 보유율이 절반 아래다(" + withCode + "/" + result.places().size() + ")");
    }

    /**
     * <b>상세의 동반 조건이 읽힌다</b> — 목록과 표기가 달라 여기서만 걸린다.
     *
     * <p>{@code acmpyTypeCd} 가 안 읽히면 모든 장소가 "조건을 모르는 반려동반" 으로 저장돼, 절반이
     * 일부구역이라는 사실이 통째로 사라진다.
     */
    @Test
    void 상세의_동반_조건이_읽힌다() {
        PetTourResult result = client().findAll(WAIT);
        String contentId = result.places().get(0).contentId();

        Optional<PetTourDetail> detail = client().findDetail(contentId, WAIT);

        assertTrue(detail.isPresent(), "상세를 못 받았다 — 경로나 파라미터명을 확인하라");
        PetTourDetail found = detail.get();
        assertEquals(contentId, found.contentId());
        // 동반 구역·동반 반려동물 중 하나라도 읽혀야 한다. 둘 다 비면 필드명이 어긋난 것이다 —
        // 실측에서 15건 중 빈값은 accompanyPet 한 건뿐이었고 area 는 전부 채워져 있었다.
        assertFalse(found.accompanyArea() == null && found.accompanyPet() == null,
                "동반 조건을 하나도 못 읽었다 — 상세 필드명(acmpyTypeCd·acmpyPsblCpam)을 확인하라");
    }

    private static void assertEquals5Digits(String legalCode) {
        assertTrue(legalCode.matches("\\d{5}"),
                "법정 시군구코드는 5자리여야 한다: " + legalCode);
    }
}
