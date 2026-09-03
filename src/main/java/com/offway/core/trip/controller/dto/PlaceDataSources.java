package com.offway.core.trip.controller.dto;

import com.offway.core.common.response.DataSource;
import com.offway.core.trip.domain.HeritagePlace;
import com.offway.core.trip.domain.LicensedPlace;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;

/**
 * 장소 식별자가 <b>어느 기관에서 온 값인지</b> 가린다(#399).
 *
 * <p>장소 풀이 셋에서 온다 — TourAPI(한국관광공사) · 지방행정인허가 · 국가유산청. 앞의 둘은 식별자에
 * 접두어가 붙어 있고({@code LIC-} · {@code HER-}), 접두어가 없으면 공사의 {@code contentId} 다.
 *
 * <p><b>접두어를 여기서 다시 적지 않는다.</b> 도메인이 이미 파싱을 소유하므로({@code parsePublicId})
 * 그것에 물어본다. 접두어 문자열을 DTO 가 또 들면 한쪽만 바뀌었을 때 조용히 틀린 출처를 표기한다.
 *
 * <p>Mapper 빈이 아니다 — 상태도 의존성도 없는 판정 한 줄이고, 이 판정을 쓰는 응답 DTO 가 여럿이라
 * 한 곳에 둔다. 각자 복사하면 새 출처가 생겼을 때 고칠 자리가 흩어진다.
 *
 * <p>패키지 밖에도 연다. 코스 응답의 슬롯도 같은 장소 풀에서 오는데, 거기서 접두어를 다시 적으면
 * 정확히 그 흩어짐이 생긴다.
 */
public final class PlaceDataSources {

    private PlaceDataSources() {
    }

    /**
     * 이 식별자를 낸 기관.
     *
     * <p>접두어가 없으면 공사로 본다. 그 판단이 맞는 이유는 장소 풀에 들어오는 경로가 셋뿐이고 나머지
     * 둘은 반드시 접두어를 달기 때문이다 — 접두어 없는 값은 TourAPI 를 지난 것뿐이다.
     */
    public static DataSource of(String placeId) {
        if (LicensedPlace.parsePublicId(placeId).isPresent()) {
            return DataSource.LOCAL_PERMIT;
        }
        if (HeritagePlace.parsePublicId(placeId).isPresent()) {
            return DataSource.KHS;
        }
        return DataSource.KTO;
    }

    /**
     * 이 목록에 <b>실제로 실린</b> 출처만 모은다.
     *
     * <p>이 페이지에 인허가 장소만 있으면 국가유산청은 빠진다 — 안 쓴 출처를 표기하면 그것도 잘못된
     * 표기다.
     */
    public static <T> Set<DataSource> of(Collection<T> items, Function<T, String> idOf) {
        Set<DataSource> sources = EnumSet.noneOf(DataSource.class);
        for (T item : items) {
            sources.add(of(idOf.apply(item)));
        }
        return Set.copyOf(sources);
    }
}
