package com.offway.core.itinerary.service;

import com.offway.core.itinerary.controller.dto.CourseSaveRequest;
import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.ItineraryException;
import com.offway.core.itinerary.repository.CourseRepository;
import com.offway.core.itinerary.service.dto.GeneratedCourse;
import com.offway.core.policy.service.PolicyService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 코스 저장·조회(#33) — 생성된 코스를 게스트의 "내 코스"로 영속화하고 다시 꺼낸다. 혜택은 저장하지 않고 조회 시점에 정책 매칭으로
 * 다시 붙인다(저장 코스가 정책 변경에 뒤처지지 않게). 애그리거트 저장이라 외부 호출 없이 짧은 트랜잭션.
 */
@Service
@RequiredArgsConstructor
public class CourseStorageService {

    private final CourseRepository courseRepository;
    private final PolicyService policyService;

    /** 게스트 코스를 저장하고, 혜택을 붙여 돌려준다. 코스 구성이 불변식을 어기면 400. */
    @Transactional
    public GeneratedCourse save(String guestId, CourseSaveRequest request) {
        Course course;
        try {
            course = request.toCourse(guestId);
        } catch (IllegalArgumentException e) {
            throw ItineraryException.invalidCourse();
        }
        return withBenefits(courseRepository.save(course));
    }

    /** 게스트의 저장 코스 목록(최신순). */
    @Transactional(readOnly = true)
    public List<Course> myCourses(String guestId) {
        List<Course> courses = courseRepository.findByGuestId(guestId);
        courses.forEach(Course::totalSlots); // 응답 직렬화는 tx 밖 — 애그리거트(days·slots)를 여기서 초기화
        return courses;
    }

    /** 저장 코스 상세(혜택 포함). 없으면 404. */
    @Transactional(readOnly = true)
    public GeneratedCourse get(long courseId) {
        Course course = courseRepository.findById(courseId).orElseThrow(ItineraryException::courseNotFound);
        course.totalSlots(); // tx 안에서 days·slots 초기화(직렬화는 tx 밖)
        return withBenefits(course);
    }

    private GeneratedCourse withBenefits(Course course) {
        List<GeneratedCourse.Benefit> benefits = policyService.matchForRegion(course.getRegionId(), LocalDate.now())
                .stream()
                .map(policy -> new GeneratedCourse.Benefit(policy.getId(), policy.getType(), policy.badgeText()))
                .toList();
        return new GeneratedCourse(course, benefits);
    }
}
