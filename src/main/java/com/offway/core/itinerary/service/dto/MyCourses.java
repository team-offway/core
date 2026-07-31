package com.offway.core.itinerary.service.dto;

import com.offway.core.itinerary.domain.Course;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * "내 코스" 목록 조회 결과 — 코스들과, 화면이 함께 보여줘야 하는 부가 상태.
 *
 * <p>차감 여부·D-day 는 코스의 상태가 아니라 <b>조회 시점의 상태</b>다. 연차 내역과 오늘 날짜에 의존하므로 도메인
 * 객체에 심지 않고 여기서 함께 내린다({@code GeneratedCourse} 가 혜택·날씨를 다루는 방식과 같다).
 *
 * @param courses 조회된 코스 (범위에 따라 정렬됨)
 * @param deductedCourseIds 연차를 차감한 코스 ID 들
 * @param today D-day 계산 기준일
 */
public record MyCourses(List<Course> courses, Set<Long> deductedCourseIds, LocalDate today) {

    public MyCourses {
        courses = List.copyOf(courses);
        deductedCourseIds = Set.copyOf(deductedCourseIds);
    }

    public boolean isDeducted(Course course) {
        return deductedCourseIds.contains(course.getId());
    }

    /**
     * 오늘 기준 남은 날 — 오늘이면 0, 미래면 양수, 지난 여행이면 음수.
     *
     * <p>여행 날짜가 없는 코스는 {@code null}. 0 으로 내리면 화면이 "오늘 출발" 로 읽는다.
     */
    public Integer dDay(Course course) {
        if (course.getTravelDate() == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(today, course.getTravelDate());
    }
}
