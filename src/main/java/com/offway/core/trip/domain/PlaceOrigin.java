package com.offway.core.trip.domain;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;

/**
 * 장소 식별자가 <b>어느 출처에서 온 값인지</b>(#399).
 *
 * <p>장소 풀이 넷에서 온다 — TourAPI · 지방행정인허가 · 국가유산청 · 문화축제표준데이터. TourAPI 를 뺀
 * 셋은 식별자에 접두어가 붙어 있고({@code LIC-} · {@code HER-} · {@code FST-}), 접두어가 없으면
 * TourAPI 의 {@code contentId} 다.
 *
 * <p><b>접두어를 여기서 다시 적지 않는다.</b> 각 도메인 타입이 이미 파싱을 소유하므로
 * ({@code parsePublicId}) 그것에 물어본다. 접두어 문자열을 또 들면 한쪽만 바뀌었을 때 조용히 틀린 출처가
 * 나간다.
 *
 * <h2>왜 도메인에 있나</h2>
 *
 * <p>처음에는 {@code trip.controller.dto} 에 뒀는데, 코스 응답({@code itinerary})도 같은 장소 풀을 실어
 * <b>controller 끼리 물리는</b> 모양이 됐다. 그러면 trip 의 응답 구현을 바꿀 때 itinerary 응답이 함께
 * 깨진다.
 *
 * <p>출처 표기용 타입({@code DataSource})을 여기서 알지 않는 것도 같은 이유다 — 도메인이 응답 계약을
 * 알면 반대 방향 결합이 생긴다. 이 enum 은 "어디서 온 값인가" 까지만 답하고, 그것을 <b>어느 기관명으로
 * 표기할지</b>는 각 응답이 정한다.
 */
public enum PlaceOrigin {

    /** TourAPI 콘텐츠 — 접두어가 없는 식별자. */
    TOUR_API,

    /** 지방행정인허가 — {@code LIC-} 접두어. */
    LICENSED,

    /** 국가유산청 — {@code HER-} 접두어. */
    HERITAGE,

    /** 전국문화축제표준데이터 — {@code FST-} 접두어(#433). */
    FESTIVAL;

    /**
     * 이 식별자를 낸 곳.
     *
     * <p>접두어가 없으면 TourAPI 로 본다. 장소 풀에 들어오는 경로가 넷뿐이고 나머지 셋은 반드시 접두어를
     * 달기 때문이다 — 접두어 없는 값은 TourAPI 를 지난 것뿐이다.
     */
    public static PlaceOrigin of(String placeId) {
        if (LicensedPlace.parsePublicId(placeId).isPresent()) {
            return LICENSED;
        }
        if (HeritagePlace.parsePublicId(placeId).isPresent()) {
            return HERITAGE;
        }
        return FestivalPlace.parsePublicId(placeId).isPresent() ? FESTIVAL : TOUR_API;
    }

    /**
     * 이 목록에 <b>실제로 실린</b> 출처만 모은다.
     *
     * <p>이 페이지에 인허가 장소만 있으면 국가유산청은 빠진다 — 안 쓴 출처를 표기하면 그것도 잘못된
     * 표기다.
     */
    public static <T> Set<PlaceOrigin> of(Collection<T> items, Function<T, String> idOf) {
        Set<PlaceOrigin> origins = EnumSet.noneOf(PlaceOrigin.class);
        for (T item : items) {
            origins.add(of(idOf.apply(item)));
        }
        return Set.copyOf(origins);
    }
}
