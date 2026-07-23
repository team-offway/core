package com.offway.core.policy.domain;

import com.offway.core.region.domain.RegionTagType;

/**
 * 7대 여행 지원 혜택 분류. 각 상수가 <b>뱃지 문구</b>와 <b>매칭 대상 지역 태그</b>를 보유한다(다형성으로 분기 제거).
 *
 * <p>지역별 참여 리스트가 확보되기 전 MVP 에서는 전부 {@link RegionTagType#POPULATION_DECLINE}(89) 을 대상으로 둔다. 실제
 * 참여 지자체 리스트가 생기면 해당 태그를 region_tag 에 시딩하고 이 매핑을 좁힌다(additive).
 */
public enum PolicyType {

    /** 디지털관광주민증 — 인구감소지역 가맹점 할인. */
    DIGITAL_TOURIST_CARD("디지털관광주민증", RegionTagType.POPULATION_DECLINE),

    /** 지역사랑 휴가지원(반값여행) — 여행경비 50% 환급. */
    REGIONAL_VOUCHER("여행경비 50% 환급", RegionTagType.POPULATION_DECLINE),

    /** 숙박세일페스타 — 숙박 할인. */
    STAY_FESTA("숙박 할인", RegionTagType.POPULATION_DECLINE),

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
