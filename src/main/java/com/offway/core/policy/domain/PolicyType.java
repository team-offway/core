package com.offway.core.policy.domain;

import com.offway.core.region.domain.RegionTagType;

/**
 * 7대 여행 지원 혜택 분류. 각 상수가 <b>뱃지 문구</b>와 <b>매칭 대상 지역 태그</b>를 보유한다(다형성으로 분기 제거).
 *
 * <p><b>대상 태그는 프로그램마다 다르다.</b> 예전에는 전부 {@link RegionTagType#POPULATION_DECLINE}(89) 을 봤는데,
 * 실제 모수가 프로그램마다 달라 안 되는 지역에 뱃지가 나갔다 — 반값여행은 16곳뿐인데 89곳에 붙어 73곳이 거짓이었다.
 * 참여 지자체를 확보한 정책부터 전용 태그로 좁힌다(#217).
 *
 * <p>확인된 모수: 반값여행 16 · 숙박세일페스타 85 · 디지털관광주민증 52 · 근로자휴가지원 전국.
 * <b>89 에서 빼고 더해 추정하지 않는다</b> — 프로그램마다 기준이 다르다.
 */
public enum PolicyType {

    /**
     * 디지털관광주민증 — 인구감소지역 가맹점 할인.
     *
     * <p><b>실제 대상은 52곳</b>인데 아직 명단을 확보하지 못해 89곳을 그대로 둔다. {@code verified=FALSE} 라
     * 노출되지 않으므로 거짓 뱃지는 나지 않는다 — 명단을 확보하면 전용 태그로 좁힌다(#217).
     */
    DIGITAL_TOURIST_CARD("디지털관광주민증", RegionTagType.POPULATION_DECLINE),

    /** 지역사랑 휴가지원(반값여행) — 여행경비 50% 환급. 2026 상반기 시범사업 16곳. */
    REGIONAL_VOUCHER("여행경비 50% 환급", RegionTagType.REGIONAL_VOUCHER),

    /** 숙박세일페스타 — 숙박 할인. 비수도권 인구감소지역 85곳. */
    STAY_FESTA("숙박 할인", RegionTagType.STAY_FESTA),

    /** 근로자 휴가지원 — 휴가비 지원. */
    WORKER_VACATION("근로자 휴가비 지원", RegionTagType.POPULATION_DECLINE),

    /** KTX·SRT 할인 — 철도 운임 할인. */
    RAIL_DISCOUNT("KTX·SRT 할인", RegionTagType.POPULATION_DECLINE),

    /** 로컬100·관광두레 — 로컬 여행 콘텐츠. */
    LOCAL_TOURISM("로컬100·관광두레", RegionTagType.POPULATION_DECLINE),

    /** 농촌체험·치유관광. */
    RURAL("농촌체험·치유관광", RegionTagType.POPULATION_DECLINE);

    private final String badgeText;
    private final RegionTagType targetTag;

    PolicyType(String badgeText, RegionTagType targetTag) {
        this.badgeText = badgeText;
        this.targetTag = targetTag;
    }

    /** 지역 카드·코스에 노출하는 짧은 뱃지 문구. */
    public String badgeText() {
        return badgeText;
    }

    /** 이 혜택이 적용되는 지역을 가려내는 태그. */
    public RegionTagType targetTag() {
        return targetTag;
    }
}
