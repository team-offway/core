package com.offway.core.itinerary.repository;

import com.offway.core.itinerary.domain.Course;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 코스 영속 port. 구현은 {@link CourseRepositoryImpl}. 애그리거트(Course→DaySchedule→Slot)를 통째로 저장·조회한다. */
public interface CourseRepository {

    Course save(Course course);

    /** 게스트의 코스 목록(최신 저장 순). */
    List<Course> findByGuestId(String guestId);

    /** 게스트의 코스 한 페이지(최신 저장 순). */
    Page<Course> findByGuestId(String guestId, Pageable pageable);

    /** 소유자 범위 상세 조회 — 남의 코스를 ID 만으로 못 보게 게스트 소유로 제한한다. */
    /** 오늘 포함 이후 여행 — 가까운 것부터. 날짜 없는 코스는 분류 근거가 없어 빠진다. */
    List<Course> findUpcoming(String guestId, LocalDate today);

    Page<Course> findUpcoming(String guestId, LocalDate today, Pageable pageable);

    /** 오늘 이전 여행 — 최근 것부터. 날짜 없는 코스는 빠진다. */
    List<Course> findPast(String guestId, LocalDate today);

    Page<Course> findPast(String guestId, LocalDate today, Pageable pageable);

    Optional<Course> findByIdAndGuestId(Long id, String guestId);

    /** 애그리거트 통째 삭제. 하위(DaySchedule·Slot)는 cascade·orphanRemoval 로 함께 지워진다. */
    void delete(Course course);
}
