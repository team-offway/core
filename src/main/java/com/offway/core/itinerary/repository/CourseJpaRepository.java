package com.offway.core.itinerary.repository;

import com.offway.core.itinerary.domain.Course;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface CourseJpaRepository extends JpaRepository<Course, Long> {

    List<Course> findByGuestIdOrderByIdDesc(String guestId);

    // 페이지 변형. 애그리거트(days·slots)는 fetch join 하지 않는다 — 컬렉션을 조인하면 페이징이
    // 메모리에서 일어난다(HHH000104). 지연 로딩은 default_batch_fetch_size 가 묶어 준다.
    Page<Course> findByGuestIdOrderByIdDesc(String guestId, Pageable pageable);

    // travelDate 가 null 인 코스는 두 조건 모두에 걸리지 않아 자연히 빠진다 — DB 마다 다른 NULL 정렬에 기대지 않는다.
    /**
     * 그 날짜에 떠나는 코스 전부(소유자 무관) — 알림 배치용(#269).
     *
     * <p>{@code GuestIdIsNotNull} 로 소유자 없는 코스(공유 링크용, #261)를 뺀다. 알릴 상대가 없는 코스다.
     */
    List<Course> findByTravelDateAndGuestIdIsNotNull(LocalDate travelDate);

    /**
     * 시작일이 이 구간에 든 코스 전부(소유자 무관) — 종료일 기준 알림 배치용(#302).
     *
     * <p><b>종료일로 직접 거르지 않는 이유.</b> 종료일은 컬럼이 아니라
     * {@code travel_date + travel_days - 1} 로 계산되는 값이라, DB 에서 비교하려면 방언에 묶인 날짜 연산을
     * 질의에 박아야 한다. 대신 시작일 범위로 후보를 좁혀 오고 정확한 판정은 도메인({@code travelEndDate()})이
     * 한다 — 계산 규칙이 한 곳에만 있게 된다.
     *
     * <p>범위 폭은 코스 최대 기간({@code Course.MAX_TRAVEL_DAYS})이라 후보가 며칠치를 넘지 않는다.
     */
    List<Course> findByTravelDateBetweenAndGuestIdIsNotNull(LocalDate from, LocalDate to);

    /**
     * 다가오는 여행 — <b>종료일이 오늘 포함 이후</b>(#325).
     *
     * <p><b>시작일이 아니라 종료일로 가른다.</b> 시작일로 가르면 2박3일 여행 둘째 날에 그 코스가 이미
     * "지난 여행" 탭으로 넘어간다 — 앱의 칩(종료일 기준)은 아직 D-DAY 라, 다녀온 여행 탭 안에 D-DAY
     * 코스가 이틀간 앉아 있게 된다.
     *
     * <p><b>native 인 이유.</b> 종료일은 컬럼이 아니라 {@code travel_date + travel_days - 1} 로 계산되는
     * 파생값이다. JPQL 은 컬럼 값만큼 날짜를 더하는 표준 문법이 없어, 이 레포가 이미 못박은 규칙
     * (로컬·테스트·운영이 전부 MySQL)을 따라 native 로 쓴다.
     *
     * <p><b>인덱스를 타지 못한다.</b> 조건이 컬럼 연산이라 travel_date 인덱스로 범위를 좁힐 수 없다.
     * 다만 앞선 {@code guest_id} 조건이 이미 한 사람의 코스로 줄여 주고, 한 사람이 담는 코스는 수십 건
     * 규모다. 그 전제가 깨질 만큼 쌓이면 종료일을 컬럼으로 저장하는 편이 낫다.
     */
    @Query(value = """
                    SELECT * FROM course
                     WHERE guest_id = :guestId
                       AND travel_date IS NOT NULL
                       AND DATE_ADD(travel_date, INTERVAL (travel_days - 1) DAY) >= :today
                     ORDER BY travel_date ASC, id DESC
                    """, countQuery = """
                    SELECT COUNT(*) FROM course
                     WHERE guest_id = :guestId
                       AND travel_date IS NOT NULL
                       AND DATE_ADD(travel_date, INTERVAL (travel_days - 1) DAY) >= :today
                    """, nativeQuery = true)
    List<Course> findUpcomingByEndDate(@Param("guestId") String guestId, @Param("today") LocalDate today);

    @Query(value = """
                    SELECT * FROM course
                     WHERE guest_id = :guestId
                       AND travel_date IS NOT NULL
                       AND DATE_ADD(travel_date, INTERVAL (travel_days - 1) DAY) >= :today
                     ORDER BY travel_date ASC, id DESC
                    """, countQuery = """
                    SELECT COUNT(*) FROM course
                     WHERE guest_id = :guestId
                       AND travel_date IS NOT NULL
                       AND DATE_ADD(travel_date, INTERVAL (travel_days - 1) DAY) >= :today
                    """, nativeQuery = true)
    Page<Course> findUpcomingByEndDate(
            @Param("guestId") String guestId, @Param("today") LocalDate today, Pageable pageable);

    /**
     * 지난 여행 — <b>종료일이 오늘보다 앞</b>(#325). 여행 중인 코스는 여기 오지 않는다.
     *
     * <p><b>정렬도 종료일이다.</b> 무엇이 PAST 인지 가르는 기준이 종료일인데 정렬만 시작일이면 둘이
     * 어긋난다 — 기간이 긴 코스는 더 일찍 떠나고도 더 늦게 끝나서, "최근 여행이 위" 라는 계약이 깨진다.
     *
     * <p>{@link #findUpcomingByEndDate} 는 반대로 <b>시작일</b> 순을 유지한다. 그쪽 계약은 "D-day 순" 이고
     * 화면에 찍히는 D-day 는 시작일로 계산되므로, 종료일로 정렬하면 목록 순서와 카드의 숫자가 어긋난다.
     *
     * <p>native 인 이유와 인덱스 이야기는 {@link #findUpcomingByEndDate} 와 같다.
     */
    @Query(value = """
                    SELECT * FROM course
                     WHERE guest_id = :guestId
                       AND travel_date IS NOT NULL
                       AND DATE_ADD(travel_date, INTERVAL (travel_days - 1) DAY) < :today
                     ORDER BY DATE_ADD(travel_date, INTERVAL (travel_days - 1) DAY) DESC, id DESC
                    """, countQuery = """
                    SELECT COUNT(*) FROM course
                     WHERE guest_id = :guestId
                       AND travel_date IS NOT NULL
                       AND DATE_ADD(travel_date, INTERVAL (travel_days - 1) DAY) < :today
                    """, nativeQuery = true)
    List<Course> findPastByEndDate(@Param("guestId") String guestId, @Param("today") LocalDate today);

    @Query(value = """
                    SELECT * FROM course
                     WHERE guest_id = :guestId
                       AND travel_date IS NOT NULL
                       AND DATE_ADD(travel_date, INTERVAL (travel_days - 1) DAY) < :today
                     ORDER BY DATE_ADD(travel_date, INTERVAL (travel_days - 1) DAY) DESC, id DESC
                    """, countQuery = """
                    SELECT COUNT(*) FROM course
                     WHERE guest_id = :guestId
                       AND travel_date IS NOT NULL
                       AND DATE_ADD(travel_date, INTERVAL (travel_days - 1) DAY) < :today
                    """, nativeQuery = true)
    Page<Course> findPastByEndDate(
            @Param("guestId") String guestId, @Param("today") LocalDate today, Pageable pageable);

    Optional<Course> findByIdAndGuestId(Long id, String guestId);

    int deleteByGuestId(String guestId);
}
