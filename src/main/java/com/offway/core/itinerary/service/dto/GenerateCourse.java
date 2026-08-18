package com.offway.core.itinerary.service.dto;

import com.offway.core.itinerary.domain.Density;
import com.offway.core.transport.domain.TransportMode;
import com.offway.core.leave.domain.StartDayLeave;
import java.time.LocalDate;
import java.util.Set;

/**
 * 코스 생성 커맨드 — 서비스 내부용(course-logic 입력). 후보지역(추천)에서 지역을 고른 뒤 위저드 값과 함께 넘어온다.
 *
 * @param regionId 코스를 만들 지역
 * @param travelDays 여행 일수(1~3)
 * @param density 일정 밀도(빡빡/널널)
 * @param transport 이동수단
 * @param originLat 출발지 위도(동선 정렬 기준)
 * @param originLng 출발지 경도
 * @param travelDate 가는 날(정책 운영기간 매칭용)
 * @param startDayLeave 첫날에 쓴 연차 — 출발 시각이 여기서 도출되고, 그 시각이 첫날 일정을 자른다(#138)
 * @param seed 후보 선택의 시작점 — 같은 씨앗이면 같은 코스, 다른 씨앗이면 다른 코스(#114)
 * @param excludePoiContentIds 빼고 짤 장소들("이 장소 말고")
 */
public record GenerateCourse(
        long regionId,
        int travelDays,
        Density density,
        TransportMode transport,
        double originLat,
        double originLng,
        LocalDate travelDate,
        StartDayLeave startDayLeave,
        long seed,
        Set<String> excludePoiContentIds) {

    public GenerateCourse {
        excludePoiContentIds = excludePoiContentIds == null ? Set.of() : Set.copyOf(excludePoiContentIds);
        // 안 보내던 호출부가 지금과 같은 결과를 받아야 한다 — 종일이 곧 예전 동작이다.
        startDayLeave = startDayLeave == null ? StartDayLeave.DEFAULT : startDayLeave;
    }

    /** 첫 생성의 씨앗 — 랭킹 상위부터 뭉친다. 재생성 전에는 이 결과가 "기존 코스" 다. */
    public static final long FIRST_SEED = 0L;

    /** 첫 생성 — 씨앗을 고르지 않으면 랭킹 상위에서 시작하고, 제외할 장소도 없다. */
    public static GenerateCourse first(
            Long regionId,
            int travelDays,
            Density density,
            TransportMode transport,
            double originLat,
            double originLng,
            LocalDate travelDate,
            StartDayLeave startDayLeave) {
        return new GenerateCourse(
                regionId, travelDays, density, transport, originLat, originLng, travelDate,
                startDayLeave, FIRST_SEED, Set.of());
    }

    /** 같은 커맨드를 다른 씨앗으로 — 재생성이 후보를 다시 모으지 않고 씨앗만 바꿔 시도한다. */
    public GenerateCourse withSeed(long newSeed) {
        return new GenerateCourse(
                regionId, travelDays, density, transport, originLat, originLng, travelDate,
                startDayLeave, newSeed, excludePoiContentIds);
    }
}
