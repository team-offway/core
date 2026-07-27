package com.offway.core.itinerary.repository;

import com.offway.core.itinerary.domain.Course;
import java.util.List;
import java.util.Optional;

/** 코스 영속 port. 구현은 {@link CourseRepositoryImpl}. 애그리거트(Course→DaySchedule→Slot)를 통째로 저장·조회한다. */
public interface CourseRepository {

    Course save(Course course);

    /** 게스트의 코스 목록(최신 저장 순). */
    List<Course> findByGuestId(String guestId);

    Optional<Course> findById(Long id);
}
