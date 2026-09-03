package com.offway.core.trip.controller.dto;

import com.offway.core.common.response.DataSource;
import com.offway.core.trip.domain.PlaceOrigin;
import java.util.Collection;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 장소의 출처를 <b>표기할 기관명</b>으로 옮긴다(#399).
 *
 * <p>"어디서 온 값인가" 는 {@link PlaceOrigin}(도메인)이 답하고, 그것을 <b>어느 기관으로 표기할지</b>는
 * 여기서 정한다. 둘을 갈라 둔 이유는 도메인이 응답 계약({@link DataSource})을 알면 반대 방향 결합이
 * 생기기 때문이다.
 *
 * <p><b>같은 매핑이 코스 응답에도 있다</b>({@code CourseResponse}). 한곳으로 모으려면 도메인이 응답을 알거나
 * {@code common} 이 도메인을 알아야 해서, 둘 다 지금 없는 방향이다. 대신 아래 {@code switch} 가
 * <b>모든 상수를 덮게</b> 두었다 — 새 출처가 생기면 두 자리가 함께 컴파일에서 깨진다
 * ({@code SlotKind.covering} 이 쓰는 것과 같은 장치다).
 */
final class PlaceDataSources {

    private PlaceDataSources() {
    }

    /** 이 식별자를 낸 기관. */
    static DataSource of(String placeId) {
        return dataSourceOf(PlaceOrigin.of(placeId));
    }

    /**
     * 이 목록에 <b>실제로 실린</b> 출처만 모은다.
     *
     * <p>이 페이지에 인허가 장소만 있으면 국가유산청은 빠진다 — 안 쓴 출처를 표기하면 그것도 잘못된 표기다.
     */
    static <T> Set<DataSource> of(Collection<T> items, Function<T, String> idOf) {
        return PlaceOrigin.of(items, idOf).stream()
                .map(PlaceDataSources::dataSourceOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 새 출처가 생기면 여기서 컴파일이 깨진다 — 표기할 기관을 안 정한 채 넘어가지 않게. */
    private static DataSource dataSourceOf(PlaceOrigin origin) {
        return switch (origin) {
            case TOUR_API -> DataSource.KTO;
            case LICENSED -> DataSource.LOCAL_PERMIT;
            case HERITAGE -> DataSource.KHS;
        };
    }
}
