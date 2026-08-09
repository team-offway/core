package com.offway.core.itinerary.service.dto;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.policy.domain.PolicyType;
import com.offway.core.transport.service.dto.TrainAccess;
import com.offway.core.weather.domain.AirQuality;
import com.offway.core.weather.domain.DailyWeather;
import java.util.List;
import java.util.Map;

/**
 * 코스 생성 결과 — 조립된 코스({@link Course} 도메인)와 응답 시점에 매칭한 혜택·날씨·열차 접근. 이들은 정책·기상·교통 상태에 의존해
 * 도메인 상태로 두지 않고 여기서 함께 내린다.
 *
 * @param course 날짜별 타임라인 코스
 * @param benefits 이 코스(지역·기간)에 적용되는 혜택 뱃지
 * @param weatherByDay Day 번호(1부터) → 그날의 날씨. 예보가 없는 Day 는 <b>키가 없다</b>(#141)
 * @param trainAccess 대중교통 코스일 때 출발지→지역 열차 접근(자차·저장 코스는 null)
 * @param regionName 코스 지역의 짧은 이름(예: 정선군) — 슬롯마다 "관광명소 · 정선군" 으로 붙는다(#141)
 * @param airQuality 코스 지역의 <b>실시간</b> 대기질. 오늘 여행 중인 코스에만 채운다 — 예보가 아니라 지금
 *     이 순간의 측정치라 다음 주 코스에 붙이면 여행일 상태로 오해된다. 그 밖에는 null
 */
public record GeneratedCourse(
        Course course,
        List<Benefit> benefits,
        Map<Integer, DailyWeather> weatherByDay,
        TrainAccess trainAccess,
        String regionName,
        AirQuality airQuality) {

    public GeneratedCourse {
        benefits = List.copyOf(benefits);
        weatherByDay = weatherByDay == null ? Map.of() : Map.copyOf(weatherByDay);
    }

    /** 날씨·열차 접근·대기질 없이(목록 조회 등). 지역명은 슬롯 표시에 쓰이므로 저장 코스도 채운다. */
    public static GeneratedCourse of(Course course, List<Benefit> benefits, String regionName) {
        return new GeneratedCourse(course, benefits, Map.of(), null, regionName, null);
    }

    /**
     * @param policyId 정책 ID
     * @param type 정책 분류
     * @param text 뱃지 문구
     */
    public record Benefit(long policyId, PolicyType type, String text) {
    }
}
