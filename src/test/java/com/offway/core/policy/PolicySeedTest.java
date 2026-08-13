package com.offway.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.policy.domain.Policy;
import com.offway.core.policy.domain.PolicyType;
import com.offway.core.policy.repository.PolicyRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PolicySeedTest {

    /** 시드가 명시한 id (R__seed_policies.sql). */
    private static final long VOUCHER_ID = 1L;
    private static final long STAY_FESTA_ID = 2L;
    private static final long DIGITAL_CARD_ID = 3L;

    @Autowired
    private PolicyRepository policyRepository;

    /**
     * 시드 id 로 읽되 <b>분류가 기대와 같은지 함께 확인</b>한다. id 만 믿으면 시드 순서가 바뀌었을 때
     * 엉뚱한 행을 검사하면서 초록이 뜬다.
     */
    private Policy seeded(long id, PolicyType expected) {
        Policy policy = policyRepository.findById(id).orElseThrow(() -> new AssertionError("시드에 없습니다: id=" + id));
        assertEquals(expected, policy.getType(), "시드 id=" + id + " 의 분류가 바뀌었습니다");
        return policy;
    }

    @Test
    void 여행자가_직접_신청하는_혜택만_검증된_정책으로_시딩된다() {
        // 회사가 참여기업으로 신청해야 하는 근로자 휴가지원, 할인이 아닌 선정 목록(로컬100)은 넣지 않는다(#217).
        //
        // **순서에 기대지 않는다.** findAllVerified() 는 ORDER BY 없이 findByVerifiedTrue() 로 내려가고
        // Policy 에도 정렬 필드가 없어, 반환 순서는 계약이 아니다. 여기서 검증하려는 것은 "무엇이 검증됐고
        // 무엇이 빠졌나" 이지 순서가 아니다 — 순서로 단언하면 DB 가 다른 순서를 줬을 때 엉뚱한 실패가 난다.
        Set<PolicyType> verified =
                policyRepository.findAllVerified().stream().map(Policy::getType).collect(Collectors.toSet());

        assertEquals(Set.of(PolicyType.REGIONAL_VOUCHER, PolicyType.STAY_FESTA), verified);
    }

    @Test
    void 디지털관광주민증은_대상_지역_미확보라_미검증이다() {
        // 실제 대상은 52곳인데 명단이 없어 아직 89곳을 본다. verified=FALSE 가 노출을 막고 있다.
        assertFalse(seeded(DIGITAL_CARD_ID, PolicyType.DIGITAL_TOURIST_CARD).isVerified());
    }

    @Test
    void 반값여행은_운영기간_안에서만_유효하다() {
        Policy voucher = seeded(VOUCHER_ID, PolicyType.REGIONAL_VOUCHER);

        assertTrue(voucher.isActiveOn(LocalDate.of(2026, 5, 15)));
        assertFalse(voucher.isActiveOn(LocalDate.of(2026, 10, 1)), "사업 종료 후");
        assertFalse(voucher.isActiveOn(LocalDate.of(2026, 3, 31)), "사업 시작 전");
    }

    @Test
    void 숙박세일페스타는_발급기간_안에서만_유효하다() {
        Policy festa = seeded(STAY_FESTA_ID, PolicyType.STAY_FESTA);

        assertTrue(festa.isActiveOn(LocalDate.of(2026, 8, 31)), "마지막 날도 유효하다");
        assertFalse(festa.isActiveOn(LocalDate.of(2026, 9, 1)));
        assertFalse(festa.isActiveOn(LocalDate.of(2026, 6, 10)));
    }

    @Test
    void 지자체별로_기간이_다른_정책은_날짜를_비우지_않고_문구로_알린다() {
        // 날짜를 null 로 두면 isActiveOn 이 "상시" 로 읽어 사업이 끝나도 뱃지가 남는다.
        // 바깥 경계는 날짜로 넣어 만료가 걸리게 하고, 지자체별 사정은 문구로 말한다(#217).
        Policy voucher = seeded(VOUCHER_ID, PolicyType.REGIONAL_VOUCHER);

        assertNotNull(voucher.getPeriodStart(), "바깥 경계가 없으면 만료가 안 걸린다");
        assertNotNull(voucher.getPeriodEnd(), "바깥 경계가 없으면 만료가 안 걸린다");
        assertTrue(voucher.getPeriodNote().contains("지자체별"), "실제=" + voucher.getPeriodNote());
    }

    @Test
    void 기간이_없는_정책은_상시_유효하다() {
        Policy card = seeded(DIGITAL_CARD_ID, PolicyType.DIGITAL_TOURIST_CARD); // period 없음

        assertTrue(card.isActiveOn(LocalDate.of(2026, 1, 1)));
        assertTrue(card.isActiveOn(LocalDate.of(2030, 12, 31)));
    }
}
