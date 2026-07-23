package com.offway.core.policy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.policy.domain.Policy;
import com.offway.core.policy.domain.PolicyType;
import com.offway.core.region.domain.RegionTagType;
import com.offway.core.region.repository.RegionTagRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PolicyMatchIntegrationTest {

    @Autowired
    private PolicyService policyService;

    @Autowired
    private RegionTagRepository regionTagRepository;

    /** POPULATION_DECLINE 태그가 확인된 지역을 결정적으로 고른다(시드 비면 명확히 실패). */
    private Long populationDeclineRegionId() {
        List<Long> ids = regionTagRepository.findRegionIdsByTag(RegionTagType.POPULATION_DECLINE);
        assertFalse(ids.isEmpty(), "POPULATION_DECLINE 지역 시드가 비어 있습니다");
        return ids.get(0);
    }

    @Test
    void 운영기간_안이면_반값여행이_뱃지로_매칭된다() {
        List<Policy> matched = policyService.matchForRegion(populationDeclineRegionId(), LocalDate.of(2026, 5, 15));

        assertEquals(1, matched.size()); // verified + 기간 유효인 정책만 (반값여행)
        assertEquals(PolicyType.REGIONAL_VOUCHER, matched.get(0).getType());
    }

    @Test
    void 운영기간_밖이면_매칭되지_않는다() {
        List<Policy> matched = policyService.matchForRegion(populationDeclineRegionId(), LocalDate.of(2026, 9, 1));

        assertTrue(matched.isEmpty()); // 반값여행 기간 종료, 디지털관광주민증은 미검증이라 제외
    }
}
