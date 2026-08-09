package com.offway.core.trip.infrastructure.datalab.dto;

import com.offway.core.trip.domain.HubAttraction;
import java.time.YearMonth;

/**
 * 중심 관광지 한 건(LocgoHubTarService1 응답).
 *
 * @param rank 지자체 안 순위(1부터)
 * @param code 데이터랩 관광지 식별자
 * @param name 관광지명
 * @param categoryLarge 대분류(관광지·음식·숙박)
 * @param categoryMedium 중분류(역사관광·문화관광 등)
 * @param lat 위도(없으면 null)
 * @param lng 경도(없으면 null)
 */
public record HubAttractionItem(
        int rank,
        String code,
        String name,
        String categoryLarge,
        String categoryMedium,
        Double lat,
        Double lng) {

    /**
     * 엔티티로 만들 수 있는가 — {@link HubAttraction} 생성자가 요구하는 값이 다 있는지 미리 본다.
     *
     * <p>어댑터가 이걸로 걸러야 하는 이유: 여기서 통과시키면 {@link #toEntity} 시점에 예외가 터지는데,
     * 그 자리는 호출자가 외부 실패로 잡는 경계 밖이라 한 건이 배치 전체를 멈춘다.
     */
    public boolean isComplete() {
        return rank >= 1 && hasText(code) && hasText(name);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** 우리 지역에 붙여 영속 엔티티로. */
    public HubAttraction toEntity(Long regionId, YearMonth baseMonth) {
        return HubAttraction.builder()
                .regionId(regionId)
                .baseMonth(baseMonth)
                .hubRank(rank)
                .hubCode(code)
                .name(name)
                .categoryLarge(categoryLarge)
                .categoryMedium(categoryMedium)
                .lat(lat)
                .lng(lng)
                .build();
    }
}
