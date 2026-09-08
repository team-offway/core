package com.offway.core.policy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.policy.domain.Policy;
import com.offway.core.policy.domain.PolicyType;
import com.offway.core.region.domain.Region;
import com.offway.core.region.domain.RegionTagType;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.region.repository.RegionTagRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 정책이 <b>실제 대상 지역에만</b> 붙는지(#217).
 *
 * <p>여기서 중요한 단언은 "붙는다" 가 아니라 <b>"안 붙는다"</b> 다. 예전에는 모든 정책이 인구감소지역 89곳 전체를
 * 대상으로 둬서, 반값여행이 실제로는 16곳뿐인데 나머지 73곳에도 뱃지가 나갔다. 붙는 것만 확인하면 그 상태도
 * 그대로 통과한다.
 *
 * <p>두 정책 기간이 겹치는 날로 고정해 본다 — 기간 밖이면 무엇을 물어도 비어서 지역 판정이 검증되지 않는다.
 */
@SpringBootTest
// 이 테스트가 정책을 직접 심으므로 롤백이 필요하다 — 안 걸면 다음 테스트의 매칭에 섞인다.
@org.springframework.transaction.annotation.Transactional
class PolicyMatchIntegrationTest {

    /** 반값여행 대상이자 비수도권 — 두 정책이 다 붙어야 한다. */
    private static final String VOUCHER_SIDO = "전남광주통합특별시";
    private static final String VOUCHER_SIGUNGU = "완도군";

    /**
     * 인구감소지역이고 비수도권이지만 반값여행 25곳에는 없다 — 숙박세일페스타만 붙어야 한다.
     *
     * <p>함평군이다. 참여 명단의 <b>함양군</b>(경남)과 한 글자 차이라 헷갈리기 쉽다(#345).
     */
    private static final String NON_VOUCHER_SIDO = "전남광주통합특별시";
    private static final String NON_VOUCHER_SIGUNGU = "함평군";

    /** 인구감소지역이지만 수도권 — 숙박세일페스타가 붙으면 안 된다. */
    private static final String METRO_SIDO = "경기도";
    private static final String METRO_SIGUNGU = "가평군";

    /**
     * 두 정책 기간이 <b>겹치는</b> 날짜. 반값여행 4/1~11/30 과 숙박세일페스타 6/11~8/31 의 교집합이다.
     *
     * <p>한쪽 기간만 보고 잡으면 다른 정책이 기간 밖이라 비어 나오고, 지역 판정이 검증되지 않은 채
     * "안 붙는다" 만 통과한다.
     */
    private static final LocalDate WITHIN_PERIOD = LocalDate.of(2026, 7, 15);

    @Autowired
    private PolicyService policyService;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private RegionTagRepository regionTagRepository;

    @Autowired
    private com.offway.core.policy.repository.PolicyRepository policyRepository;

    private Long regionId(String sido, String sigungu) {
        return regionRepository.findAll().stream()
                .filter(region -> sido.equals(region.getSido()) && sigungu.equals(region.getSigungu()))
                .map(Region::getId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("지역 시드에 없습니다: " + sido + " " + sigungu));
    }

    private List<PolicyType> matchedTypes(String sido, String sigungu) {
        return policyService.matchForRegion(regionId(sido, sigungu), WITHIN_PERIOD).stream()
                .map(Policy::getType)
                .toList();
    }

    @Test
    void 반값여행_대상_지역에는_반값여행이_붙는다() {
        assertTrue(matchedTypes(VOUCHER_SIDO, VOUCHER_SIGUNGU).contains(PolicyType.REGIONAL_VOUCHER));
    }

    @Test
    void 반값여행_대상이_아닌_인구감소지역에는_반값여행이_붙지_않는다() {
        // 이 단언이 이 PR 의 핵심이다. 예전에는 89곳 전체에 붙어 73곳이 거짓 뱃지였다.
        // 사전 신청까지 필요한 혜택이라 사용자가 신청하러 갔다가 대상이 아님을 알게 된다.
        List<PolicyType> matched = matchedTypes(NON_VOUCHER_SIDO, NON_VOUCHER_SIGUNGU);

        assertFalse(matched.contains(PolicyType.REGIONAL_VOUCHER), "실제=" + matched);
    }

    @Test
    void 비수도권_인구감소지역에는_숙박세일페스타가_붙는다() {
        assertTrue(matchedTypes(NON_VOUCHER_SIDO, NON_VOUCHER_SIGUNGU).contains(PolicyType.STAY_FESTA));
    }

    /**
     * 숙박세일페스타는 비수도권 85곳이다. 가평·연천·강화·옹진 넷은 89곳에 들지만 대상이 아니다. 서울에서 가까워
     * 코스에 자주 뽑히는 지역이라, 안 걸러내면 눈에 잘 띄는 자리에서 거짓 뱃지가 난다.
     *
     * <p><b>넷을 다 본다.</b> 한 곳만 확인하면 나머지 셋이 잘못 포함돼도 초록이 뜬다 — 제외 규칙은
     * {@code sido NOT IN (...)} 한 줄이라 시도 이름이 어긋나면 그 시도 전체가 한꺼번에 새는데, 표본 하나로는
     * 어느 시도가 샜는지도 못 가린다(인천 강화·옹진과 경기 가평·연천은 서로 다른 시도다).
     */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({"경기도,가평군", "경기도,연천군", "인천광역시,강화군", "인천광역시,옹진군"})
    void 수도권_인구감소지역에는_숙박세일페스타가_붙지_않는다(String sido, String sigungu) {
        List<PolicyType> matched = matchedTypes(sido, sigungu);

        assertFalse(matched.contains(PolicyType.STAY_FESTA), sido + " " + sigungu + " 실제=" + matched);
    }

    @Test
    void 두_정책_대상이_겹치는_지역에는_둘_다_붙는다() {
        List<PolicyType> matched = matchedTypes(VOUCHER_SIDO, VOUCHER_SIGUNGU);

        assertTrue(matched.contains(PolicyType.REGIONAL_VOUCHER), "실제=" + matched);
        assertTrue(matched.contains(PolicyType.STAY_FESTA), "실제=" + matched);
    }

    /**
     * <b>미검증 정책은 대상 지역이어도 안 붙는다.</b>
     *
     * <p>정책을 <b>이 테스트가 직접 심는다.</b> 예전에는 시드의 디지털관광주민증이 미검증이라 그것을
     * 예시로 썼는데, 대상 명단을 확보해 검증으로 올라가면서(#498) 그 전제가 사라졌다. 시드 상태에
     * 기대면 시드가 바뀔 때마다 엉뚱한 곳이 깨진다.
     */
    @Test
    void 미검증_정책은_대상_지역이어도_붙지_않는다() {
        policyRepository.save(Policy.builder()
                .type(PolicyType.WORKER_VACATION)
                .name("아직 확인 못 한 혜택")
                .benefitDetail("상세 미확정")
                .targetAudience("전 국민")
                .verified(false)
                .checkedOn(LocalDate.of(2026, 9, 7))
                .build());

        assertFalse(matchedTypes(VOUCHER_SIDO, VOUCHER_SIGUNGU).contains(PolicyType.WORKER_VACATION));
    }

    /**
     * 기간이 있는 정책은 그 밖에서 안 붙는다.
     *
     * <p><b>기간이 없는 정책은 여기서 빠진다.</b> {@code null} 은 "모른다" 가 아니라 "상시" 라는 것이
     * {@code isActiveOn} 의 계약이고, 디지털관광주민증이 실제로 그렇다 — 연중 언제 가도 쓸 수 있다.
     * 그래서 "아무것도 안 붙는다" 가 아니라 <b>기간 있는 것만 빠진다</b> 로 단언한다.
     */
    @Test
    void 운영기간_밖이면_기간이_있는_정책은_매칭되지_않는다() {
        // 반값여행이 11-30 까지라 12월로 잡는다(#345).
        List<Policy> matched =
                policyService.matchForRegion(regionId(VOUCHER_SIDO, VOUCHER_SIGUNGU), LocalDate.of(2026, 12, 1));

        List<PolicyType> types = matched.stream().map(Policy::getType).toList();
        assertFalse(types.contains(PolicyType.REGIONAL_VOUCHER), "실제=" + types);
        assertFalse(types.contains(PolicyType.STAY_FESTA), "실제=" + types);
        assertTrue(matched.stream().allMatch(policy -> policy.isActiveOn(LocalDate.of(2026, 12, 1))),
                "기간 밖 정책이 남아 있다: " + types);
    }

    @Test
    void 프로그램별_대상_지역_수가_시드와_맞는다() {
        // 숫자가 틀어지면 시드 SQL 의 조인이나 지역 시드가 바뀐 것이다.
        assertEquals(25, regionTagRepository.countByTag(RegionTagType.REGIONAL_VOUCHER), "반값여행 참여 25곳");
        assertEquals(85, regionTagRepository.countByTag(RegionTagType.STAY_FESTA), "비수도권 인구감소지역 85곳");
        assertEquals(89, regionTagRepository.countByTag(RegionTagType.POPULATION_DECLINE), "행안부 고시 89곳");
    }
}
