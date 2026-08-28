package com.offway.core.itinerary.service.dto;

import com.offway.core.itinerary.domain.Density;
import com.offway.core.transport.domain.TransportMode;
import com.offway.core.leave.domain.StartDayLeave;
import java.time.LocalDate;
import java.util.Set;
import lombok.Builder;

/**
 * 코스 생성 커맨드 — 서비스 내부용(course-logic 입력). 후보지역(추천)에서 지역을 고른 뒤 위저드 값과 함께 넘어온다.
 *
 * <p><b>조립이라 빌더다</b>(#300). 인자가 열이고 그중 위도·경도가 같은 타입으로 나란히 있어,
 * 위치 인수로 넘기면 둘이 뒤바뀌어도 컴파일이 통과한다 — 출발지가 엉뚱한 곳에 찍히고 동선이 통째로
 * 어긋난다.
 *
 * <p><b>씨앗과 제외 목록은 안 적어도 된다.</b> 빌더가 비워 두면 {@code seed} 는 {@link #FIRST_SEED}(0),
 * 제외 목록은 빈 집합이 된다 — 그게 곧 "첫 생성" 이다.
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
@Builder(toBuilder = true)
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

    /**
     * 같은 커맨드를 다른 씨앗으로 — 재생성이 후보를 다시 모으지 않고 씨앗만 바꿔 시도한다.
     *
     * <p>{@code toBuilder} 로 베낀다. 열 개를 손으로 다시 나열하면 필드가 늘 때 여기 빠뜨리기 쉽고,
     * 그러면 재생성만 조용히 옛 값을 쓴다.
     */
    public GenerateCourse withSeed(long newSeed) {
        return toBuilder().seed(newSeed).build();
    }
}
