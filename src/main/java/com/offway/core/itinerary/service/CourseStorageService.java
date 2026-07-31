package com.offway.core.itinerary.service;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.CourseScope;
import com.offway.core.itinerary.service.dto.MyCourses;
import com.offway.core.leave.service.MyLeaveService;
import java.time.LocalDate;
import java.time.ZoneId;
import com.offway.core.itinerary.domain.ItineraryException;
import com.offway.core.itinerary.repository.CourseRepository;
import com.offway.core.itinerary.service.dto.GeneratedCourse;
import com.offway.core.policy.service.PolicyService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 코스 저장·조회(#33) — 생성된 코스를 게스트의 "내 코스"로 영속화하고 다시 꺼낸다. 혜택은 저장하지 않고 조회 시점에 정책 매칭으로
 * 다시 붙인다(저장 코스가 정책 변경에 뒤처지지 않게). 애그리거트 저장이라 외부 호출 없이 짧은 트랜잭션.
 */
@Service
@RequiredArgsConstructor
public class CourseStorageService {

    /** D-day·다가오는 여행 판정 기준 시간대. 서비스가 한국 여행을 다루므로 사용자 로캘과 무관하게 KST 다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final CourseRepository courseRepository;
    private final PolicyService policyService;
    private final CoursePersistenceService coursePersistenceService;
    private final MyLeaveService myLeaveService;

    /** 이미 조립된 게스트 코스를 저장하고, 혜택을 붙여 돌려준다. 구성 검증·계약 예외 번역은 입력 경계(요청 DTO)가 소유한다. */
    @Transactional
    public GeneratedCourse save(Course course) {
        return withBenefits(courseRepository.save(course));
    }

    /**
     * 게스트의 저장 코스 목록 — 보는 범위({@link CourseScope})에 따라 다가오는 여행 · 지난 여행 · 전부.
     *
     * <p>어느 코스를 연차 차감했는지 함께 준다. 화면이 "확정함" 을 표시하려면 필요한데, <b>코스마다 물으면 코스 수만큼
     * 쿼리가 늘어난다</b> — 한 번에 모아 온다.
     */
    @Transactional(readOnly = true)
    public MyCourses myCourses(String guestId, CourseScope scope) {
        LocalDate today = LocalDate.now(SERVICE_ZONE);
        List<Course> courses = scope.find(courseRepository, guestId, today);
        courses.forEach(Course::totalSlots); // 응답 직렬화는 tx 밖 — 애그리거트(days·slots)를 여기서 초기화
        return new MyCourses(courses, myLeaveService.deductedCourseIds(guestId), today);
    }

    /**
     * 게스트 소유의 저장 코스 상세(혜택 포함). 소유자 범위로만 조회해 남의 코스를 ID 만으로 볼 수 없게 한다. 없거나 소유자가
     * 아니면 존재 여부를 흘리지 않도록 똑같이 404.
     */
    @Transactional(readOnly = true)
    public GeneratedCourse get(String guestId, long courseId) {
        Course course = courseRepository
                .findByIdAndGuestId(courseId, guestId)
                .orElseThrow(ItineraryException::courseNotFound);
        course.totalSlots(); // tx 안에서 days·slots 초기화(직렬화는 tx 밖)
        return withBenefits(course);
    }

    /**
     * 게스트 소유의 저장 코스를 지운다.
     *
     * <p>조회와 <b>같은 규칙</b>이다 — 없거나 남의 코스면 똑같이 404 다. 403 으로 나누면 "그 ID 는 존재하는데 네 것이
     * 아니다" 를 알려주는 셈이라, ID 를 훑어 남의 코스 존재를 확인할 수 있다.
     *
     * <p>hard delete 다. 하위(DaySchedule·Slot)는 애그리거트 내부라 {@code cascade = ALL} ·
     * {@code orphanRemoval} 로 함께 지워진다.
     *
     * <p><b>이 메서드에 트랜잭션을 걸지 않는다.</b> 동시 삭제 충돌은 flush·commit 시점에 드러나므로 같은 트랜잭션
     * 안에서는 잡을 수 없다 — {@link CoursePersistenceService} 가 커밋까지 끝낸 뒤 결과를 여기서 받는다.
     */
    public void delete(String guestId, long courseId) {
        try {
            coursePersistenceService.deleteOwned(guestId, courseId);
        } catch (OptimisticLockingFailureException e) {
            // 남이 먼저 지웠다. "없다" 가 정확한 답이고, 순차 재삭제(두 번째 요청)와 같은 계약이 된다.
            throw ItineraryException.courseNotFound();
        }
    }

    private GeneratedCourse withBenefits(Course course) {
        List<GeneratedCourse.Benefit> benefits = policyService.matchForRegion(course.getRegionId(), LocalDate.now())
                .stream()
                .map(policy -> new GeneratedCourse.Benefit(policy.getId(), policy.getType(), policy.badgeText()))
                .toList();
        return GeneratedCourse.of(course, benefits); // 저장 코스는 여행 날짜가 없어 날씨 미부착
    }
}
