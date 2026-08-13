package com.offway.core.itinerary.service.dto;

import com.offway.core.itinerary.domain.Course;
import java.util.List;
import java.util.Map;

/**
 * 홈 모달이 물어볼 지난 여행들(#116) — 아직 "다녀오셨나요?" 에 답하지 않은 것.
 *
 * @param trips 물어볼 여행 (최근 여행부터)
 * @param regionNames 지역 ID → 지역명. 코스마다 조회하면 N+1 이라 한 번에 모아 온다
 * @param consumedLeaveDays 코스 ID → 차감될 연차 일수. 모달의 "연차 N일 차감" 이다
 * @param remainingDays 지금 남은 연차. 아직 설정한 적 없으면 {@code null} — 모달이 "13일 → 10일" 을 못 그린다
 */
public record PendingTrips(
        List<Course> trips,
        Map<Long, String> regionNames,
        Map<Long, Double> consumedLeaveDays,
        Double remainingDays) {

    public PendingTrips {
        trips = List.copyOf(trips);
        regionNames = Map.copyOf(regionNames);
        consumedLeaveDays = Map.copyOf(consumedLeaveDays);
    }

    /** 지역명을 모르면 빈 문자열이 아니라 {@code null} — 화면이 "이름 없음" 과 "아직 못 불러옴" 을 구분할 수 있게. */
    public String regionNameOf(Course course) {
        return regionNames.get(course.getRegionId());
    }

    public Double consumedLeaveDaysOf(Course course) {
        return consumedLeaveDays.get(course.getId());
    }
}
