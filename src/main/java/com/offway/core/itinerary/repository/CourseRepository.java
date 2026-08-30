package com.offway.core.itinerary.repository;

import com.offway.core.itinerary.domain.Course;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 코스 영속 port. 구현은 {@link CourseRepositoryImpl}. 애그리거트(Course→DaySchedule→Slot)를 통째로 저장·조회한다. */
public interface CourseRepository {

    Course save(Course course);

    /** 사용자의 코스 목록(최신 저장 순). */
    List<Course> findByUserId(UUID userId);

    /** 사용자의 코스 한 페이지(최신 저장 순). */
    Page<Course> findByUserId(UUID userId, Pageable pageable);

    /**
     * 그 날짜에 떠나는 <b>모든 소유자</b>의 저장 코스 — 알림 배치가 쓴다(#269).
     *
     * <p>다른 조회와 달리 소유자로 좁히지 않는다. 배치는 특정 사용자가 아니라 그 날짜에 해당하는 사람
     * 전부를 찾아야 하기 때문이다. 소유자 없는 코스(공유 링크용, #261)는 알릴 상대가 없어 빠진다.
     */
    List<Course> findByTravelDate(LocalDate travelDate);

    /**
     * 그 날 여행이 <b>끝난</b> 코스 전부(소유자 무관) — 종료 다음 날 알림 배치용(#302).
     *
     * <p>소유자 없는 코스(공유 링크용, #261)는 빠진다 — 알릴 상대가 없다.
     */
    List<Course> findEndedOn(LocalDate endedOn);

    /** 오늘 포함 이후 여행 — 가까운 것부터. 날짜 없는 코스는 분류 근거가 없어 빠진다. */
    List<Course> findUpcoming(UUID userId, LocalDate today);

    Page<Course> findUpcoming(UUID userId, LocalDate today, Pageable pageable);

    /** 오늘 이전 여행 — 최근 것부터. 날짜 없는 코스는 빠진다. */
    List<Course> findPast(UUID userId, LocalDate today);

    Page<Course> findPast(UUID userId, LocalDate today, Pageable pageable);

    /** 소유자 범위 상세 조회 — 남의 코스를 ID 만으로 못 보게 소유자로 제한한다. */
    Optional<Course> findByIdAndUserId(Long id, UUID userId);

    /**
     * 소유자 무관 조회 — <b>공유 링크 전용</b>(#143).
     *
     * <p>일반 상세 조회는 반드시 {@link #findByIdAndUserId} 를 쓴다. 이 메서드로 열면 순번 ID 만으로 남의
     * 코스를 훑을 수 있다. 여기서 접근을 막는 것은 소유자가 아니라 <b>추측 불가능한 토큰</b>이다.
     */
    Optional<Course> findById(Long id);

    /**
     * 여러 코스를 한 번에 — 알림 목록이 지역명을 채울 때 쓴다(#356).
     *
     * <p>한 건씩 읽으면 알림 20건이 조회 20번이 된다. 그리고 <b>없는 id 는 그냥 빠진다</b> — 알림은 코스가
     * 지워져도 남으므로(raw ID 참조) 못 찾는 것이 정상이고, 호출부가 그때 지역명을 비운다.
     */
    List<Course> findByIds(List<Long> ids);

    /** 애그리거트 통째 삭제. 하위(DaySchedule·Slot)는 cascade·orphanRemoval 로 함께 지워진다. */
    void delete(Course course);

    /**
     * 탈퇴 — 이 소유자의 코스를 전부 지운다.
     *
     * <p><b>벌크 JPQL 이 아니라 엔티티를 지운다.</b> {@code delete from Course where ...} 는 빠르지만
     * {@code @OneToMany(cascade, orphanRemoval)} 를 건너뛰어 {@code day_schedule}·{@code slot} 이 고아로
     * 남는다 — FK 제약이 없어 DB 도 막아주지 않는다.
     *
     * <p>{@code course_share} 는 함께 지우지 않는다. 남겨야 이미 뿌린 공유 링크가 410(게시자가 삭제함)으로
     * 답한다.
     *
     * @return 지운 코스 수
     */
    int deleteByUserId(UUID userId);
}
