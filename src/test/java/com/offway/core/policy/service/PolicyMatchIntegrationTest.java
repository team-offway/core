package com.offway.core.policy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.policy.domain.Policy;
import com.offway.core.policy.domain.PolicyType;
import com.offway.core.region.repository.RegionRepository;
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
    private RegionRepository regionRepository;

    private Long anyRegionId() {
        return regionRepository.findAll().get(0).getId(); // 89 전부 POPULATION_DECLINE 태그
    }

    @Test
    void 운영기간_안이면_반값여행이_뱃지로_매칭된다() {
        List<Policy> matched = policyService.matchForRegion(anyRegionId(), LocalDate.of(2026, 5, 15));

        assertEquals(1, matched.size()); // verified + 기간 유효인 정책만 (반값여행)
        assertEquals(PolicyType.REGIONAL_VOUCHER, matched.get(0).getType());
    }

    @Test
    void 운영기간_밖이면_매칭되지_않는다() {
        List<Policy> matched = policyService.matchForRegion(anyRegionId(), LocalDate.of(2026, 9, 1));

        assertTrue(matched.isEmpty()); // 반값여행 기간 종료, 디지털관광주민증은 미검증이라 제외
    }
}
