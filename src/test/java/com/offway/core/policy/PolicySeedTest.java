package com.offway.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.policy.domain.Policy;
import com.offway.core.policy.domain.PolicyType;
import com.offway.core.policy.repository.PolicyRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PolicySeedTest {

    @Autowired
    private PolicyRepository policyRepository;

    @Test
    void 반값여행_정책이_검증된_정책으로_시딩된다() {
        List<Policy> verified = policyRepository.findAllVerified();

        assertEquals(1, verified.size());
        Policy voucher = verified.get(0);
        assertEquals(PolicyType.REGIONAL_VOUCHER, voucher.getType());
        assertEquals("여행경비 50% 환급", voucher.badgeText());
    }

    @Test
    void 디지털관광주민증은_미검증으로_시딩된다() {
        Policy card = policyRepository.findById(2L).orElseThrow();

        assertEquals(PolicyType.DIGITAL_TOURIST_CARD, card.getType());
        assertFalse(card.isVerified());
    }

    @Test
    void 반값여행은_운영기간_안에서만_유효하다() {
        Policy voucher = policyRepository.findById(1L).orElseThrow();

        assertTrue(voucher.isActiveOn(LocalDate.of(2026, 5, 15))); // 4~8월 안
        assertFalse(voucher.isActiveOn(LocalDate.of(2026, 9, 1))); // 종료 후
    }

    @Test
    void 기간이_없는_정책은_상시_유효하다() {
        Policy card = policyRepository.findById(2L).orElseThrow(); // period 없음

        assertTrue(card.isActiveOn(LocalDate.of(2026, 1, 1)));
        assertTrue(card.isActiveOn(LocalDate.of(2030, 12, 31)));
    }
}
