package com.offway.core.itinerary.service.dto;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.policy.domain.PolicyType;
import com.offway.core.transport.service.dto.TrainAccess;
import com.offway.core.weather.domain.DailyWeather;
import java.util.List;

/**
 * 코스 생성 결과 — 조립된 코스({@link Course} 도메인)와 응답 시점에 매칭한 혜택·날씨·열차 접근. 이들은 정책·기상·교통 상태에 의존해
 * 도메인 상태로 두지 않고 여기서 함께 내린다.
 *
 * @param course 날짜별 타임라인 코스
 * @param benefits 이 코스(지역·기간)에 적용되는 혜택 뱃지
 * @param weather 여행 날짜의 코스 지역 날씨(생성 시점만 — 저장 코스는 날짜가 없어 null, 미조회·실패도 null)
 * @param trainAccess 대중교통 코스일 때 출발지→지역 열차 접근(자차·저장 코스는 null)
 */
public record GeneratedCourse(Course course, List<Benefit> benefits, DailyWeather weather, TrainAccess trainAccess) {

    public GeneratedCourse {
        benefits = List.copyOf(benefits);
    }

    /** 날씨·열차 접근 없이(저장 코스·조회 등). */
    public static GeneratedCourse of(Course course, List<Benefit> benefits) {
        return new GeneratedCourse(course, benefits, null, null);
    }

    /**
     * @param policyId 정책 ID
     * @param type 정책 분류
     * @param text 뱃지 문구
     */
    public record Benefit(long policyId, PolicyType type, String text) {
    }
}
