package com.offway.core.trip.controller.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.offway.core.policy.domain.Policy;
import com.offway.core.policy.domain.PolicyType;
import com.offway.core.trip.service.dto.RegionBenefit;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 혜택 뱃지가 <b>신청 주소를 잃지 않고</b> 화면까지 닿는지(#413).
 *
 * <p>이 PR 이 하는 일이 그 값 하나를 옮기는 것이라, 중간에서 떨어지면 PR 이 아무 일도 안 한 것이 된다.
 * 그런데 떨어져도 응답은 멀쩡해 보인다 — 링크 아이콘만 계속 죽어 있을 뿐이다.
 */
class BenefitResponseTest {

    private static RegionBenefit 혜택(String applyUrl) {
        return RegionBenefit.builder()
                .policyId(2L)
                .type(PolicyType.STAY_FESTA)
                .text(PolicyType.STAY_FESTA.badgeText())
                .applyUrl(applyUrl)
                .build();
    }

    @Test
    void 신청_주소가_있으면_화면까지_실린다() {
        assertEquals("https://ktostay.visitkorea.or.kr",
                BenefitResponse.from(혜택("https://ktostay.visitkorea.or.kr")).applyUrl());
    }

    /**
     * 주소를 아직 안 적은 정책은 <b>null</b> 이다 — 지어내지 않는다.
     *
     * <p>아직 데이터가 없는 분류가 남아 있고(#119), 채워 넣는 동안 주소가 빈 정책이 실제로 존재한다.
     * 그때 앱은 링크 아이콘만 접는다.
     */
    @Test
    void 신청_주소가_없으면_null_이다() {
        assertNull(BenefitResponse.from(혜택(null)).applyUrl());
    }

    /** 눌렀을 때 갈 곳과 뱃지 문구도 함께 옮겨진다 — 장소 상세에는 예전에 이것들조차 없었다. */
    @Test
    void 뱃지_문구와_정책_id도_함께_온다() {
        BenefitResponse 응답 = BenefitResponse.from(혜택("https://example.com"));

        assertEquals(2L, 응답.policyId());
        assertEquals(PolicyType.STAY_FESTA, 응답.policyType());
        assertEquals(PolicyType.STAY_FESTA.badgeText(), 응답.text());
    }

    /** 혜택이 없는 화면은 그대로 없다 — 빈 객체를 만들면 앱이 뱃지 자리를 그린다. */
    @Test
    void 혜택이_없으면_null_을_그대로_돌려준다() {
        assertNull(BenefitResponse.from(null));
    }

    /**
     * 저장 안 된 정책은 <b>이름 있는 예외</b>로 끊는다.
     *
     * <p>{@code policyId} 는 앱이 혜택 상세로 갈 때 쓰는 값이라 없으면 뜻이 없다. 그냥 두면 언박싱
     * NPE 가 나 원인이 안 보인다 — 이 테스트를 쓰다가 실제로 그 NPE 를 만났다.
     *
     * <p>저장된 정책에서 뽑는 경로는 여기서 못 본다(id 를 심을 방법이 없다). 그쪽은 실제 DB 를 쓰는
     * {@code PoiDetailIntegrationTest} 가 본다.
     */
    @Test
    void 저장_안_된_정책은_받지_않는다() {
        Policy 저장전 = Policy.builder()
                .type(PolicyType.STAY_FESTA)
                .name("숙박세일페스타")
                .benefitDetail("숙박비 할인 쿠폰")
                .targetAudience("전 국민")
                .periodStart(LocalDate.of(2026, 9, 1))
                .periodEnd(LocalDate.of(2026, 12, 31))
                .applyUrl("https://example.com")
                .verified(true)
                .checkedOn(LocalDate.of(2026, 9, 1))
                .build();

        assertThrows(NullPointerException.class, () -> RegionBenefit.from(저장전));
    }
}
