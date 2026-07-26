package com.offway.core.itinerary.service.dto;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.policy.domain.PolicyType;
import java.util.List;

/**
 * 코스 생성 결과 — 조립된 코스({@link Course} 도메인)와 응답 시점에 매칭한 혜택. 혜택·비용은 정책 상태에 의존해 도메인 상태로 두지
 * 않고 여기서 함께 내린다.
 *
 * @param course 날짜별 타임라인 코스
 * @param benefits 이 코스(지역·기간)에 적용되는 혜택 뱃지
 */
public record GeneratedCourse(Course course, List<Benefit> benefits) {

    public GeneratedCourse {
        benefits = List.copyOf(benefits);
    }

    /**
     * @param policyId 정책 ID
     * @param type 정책 분류
     * @param text 뱃지 문구
     */
    public record Benefit(long policyId, PolicyType type, String text) {
    }
}
