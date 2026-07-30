package com.offway.core.trip.infrastructure.datalab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.infrastructure.datalab.dto.RegionVisitor;
import com.offway.core.trip.infrastructure.datalab.dto.TourVisitorResult;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 관광빅데이터 실제 외부 호출 E2E — data.go.kr 을 stub 없이 직접 부른다. CI 기본 실행에서 제외한다
 * ({@code DATA_GO_KR_SERVICE_KEY} 있을 때만).
 *
 * <p>랭킹 매칭이 기대는 <b>세 전제</b>를 지킨다. 셋 중 하나가 깨지면 매칭이 예외 없이 <b>조용히</b> 어긋난다 — 빈 집계라
 * 방문자 가중치가 0이 되거나, 엉뚱한 지역 값이 섞이거나, 특정 지역만 관측일수 0이 돼 전국 평균 점수를 받는다. 셋 다 로그 없이
 * 순위만 틀어지므로 실호출로 못 박아 둔다.
 *
 * <ol>
 *   <li>{@code signguCode} 는 법정 시군구코드 <b>5자리</b>다 — {@code region.legal_code} backfill 의 정본.
 *   <li>{@code region.legal_code} <b>89곳 전부</b>가 응답에 존재한다 — 행정구역 개편으로 코드가 낡으면 여기서 깨진다.
 *   <li>동명 시군구는 <b>서로 다른 코드</b>로 온다 — 지명 대신 코드로 집계하는 이유 자체.
 * </ol>
 *
 * <p>실 빈({@code TourDataLabClient} · {@code externalWebClient})을 그대로 쓴다. WebClient 를 직접 만들면 운영의
 * {@code maxInMemorySize}(2MB) 설정이 빠져 응답을 못 받는다 — 7일치가 약 1MB(5,628건)다. 참고로 한 달치는 약 4MB 로
 * 운영 한계도 넘기 때문에, 관측 창을 한 달이 아니라 발행된 달의 마지막 한 주로 잡는다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DATA_GO_KR_SERVICE_KEY", matches = ".+")
class TourDataLabClientE2ETest {

    /** 관측 창 — 발행된 달의 마지막 한 주(서비스와 같은 규칙). */
    private static final int OBSERVE_SPAN_DAYS = 7;

    /** 완결된 달만 월 단위로 발행되므로, 지난달이 비어 있으면 이전 달로 물러선다(서비스와 같은 규칙). */
    private static final int MAX_MONTHS_BACK = 3;

    private static final int PAGE_SIZE = 10_000;

    @Autowired
    private TourDataLabClient client;

    @Autowired
    private RegionRepository regionRepository;

    /** 가장 최근 발행된 달의 관측 창을 가져온다. 끝까지 비면 AssertionError — 발행 규칙이 바뀐 신호다. */
    private List<RegionVisitor> mostRecentPublished() {
        YearMonth month = YearMonth.from(LocalDate.now()).minusMonths(1);
        for (int back = 0; back < MAX_MONTHS_BACK; back++, month = month.minusMonths(1)) {
            LocalDate to = month.atEndOfMonth();
            TourVisitorResult result =
                    client.findRegionVisitors(to.minusDays(OBSERVE_SPAN_DAYS - 1), to, 1, PAGE_SIZE);
            if (!result.items().isEmpty()) {
                return result.items();
            }
        }
        throw new AssertionError(
                "최근 " + MAX_MONTHS_BACK + "개월이 모두 비었습니다 — 발행 주기가 바뀌었거나 서비스가 중단됐습니다");
    }

    /** 법정 시군구코드 형태 — 숫자 5자리. 길이만 보면 {@code ABCDE} 같은 값도 통과해 형식 변경을 놓친다. */
    private static final Pattern FIVE_DIGITS = Pattern.compile("\\d{5}");

    @Test
    void 시군구코드는_숫자_5자리_법정코드로_온다() {
        Set<String> codes = mostRecentPublished().stream()
                .map(RegionVisitor::signguCode)
                .collect(Collectors.toSet());

        List<String> malformed = codes.stream()
                .filter(code -> !FIVE_DIGITS.matcher(code).matches())
                .toList();
        assertTrue(
                malformed.isEmpty(),
                "signguCode 는 숫자 5자리여야 한다 — region.legal_code backfill 의 정본이다. 어긋난 값=" + malformed);
    }

    /**
     * 89곳 중 <b>한 곳이라도</b> 빠지면 실패한다. 빠진 지역은 관측일수 0이 돼 베이지안 prior 로 <b>전국 평균</b> 점수를
     * 받는다. 인구감소지역은 대부분 전국 평균보다 한산하므로, 코드가 낡은 지역이 실제보다 <b>높게</b> 올라간다 — 경고도 예외도
     * 없이 순위만 틀어지는 종류의 실패다.
     */
    @Test
    void region_에_backfill_한_법정코드_89곳이_전부_응답에_있다() {
        Set<String> responseCodes = mostRecentPublished().stream()
                .map(RegionVisitor::signguCode)
                .collect(Collectors.toSet());

        List<Region> regions = regionRepository.findAll();
        List<String> missing = regions.stream()
                .map(Region::getLegalCode)
                .filter(legalCode -> !responseCodes.contains(legalCode))
                .toList();

        assertTrue(
                missing.isEmpty(),
                "응답에 없는 legal_code=" + missing + " — 행정구역 개편으로 코드가 낡았다면 보정 마이그레이션이 필요하다");
        assertEquals(89, regions.size(), "인구감소지역 마스터가 89곳이어야 한다(고시 개정 시 이 수도 함께 갱신)");
    }

    @Test
    void 동명_시군구는_서로_다른_코드로_온다() {
        Map<String, Set<String>> codesByName = mostRecentPublished().stream()
                .collect(Collectors.groupingBy(
                        RegionVisitor::signguName,
                        Collectors.mapping(RegionVisitor::signguCode, Collectors.toSet())));

        // 이 지명들은 전국에 여러 곳 있다. 코드가 하나로 합쳐져 오면 지명·코드 어느 쪽으로도 지역을 특정할 수 없다.
        Map<String, Integer> expectedAtLeast = Map.of("동구", 2, "서구", 2, "중구", 2, "고성군", 2);
        expectedAtLeast.forEach((name, atLeast) -> {
            Set<String> codes = codesByName.getOrDefault(name, Set.of());
            assertTrue(
                    codes.size() >= atLeast,
                    "'" + name + "' 은 전국에 여러 곳이라 코드가 " + atLeast + "개 이상이어야 한다. 실제=" + codes);
        });

        // 지명으로 집계하면 안 되는 이유 — 지명 하나에 코드가 여럿 매달린다.
        long ambiguousNames = codesByName.values().stream().filter(codes -> codes.size() > 1).count();
        assertTrue(ambiguousNames > 0, "동명 시군구가 없다면 지명 집계도 안전하다는 뜻 — 이 변경의 전제가 무너진다");
    }
}
