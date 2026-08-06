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
