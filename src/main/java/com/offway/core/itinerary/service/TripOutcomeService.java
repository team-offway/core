package com.offway.core.itinerary.service;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.CourseScope;
import com.offway.core.itinerary.domain.ItineraryException;
import com.offway.core.itinerary.domain.TripOutcome;
import com.offway.core.itinerary.domain.VisitOutcome;
import com.offway.core.itinerary.repository.CourseRepository;
import com.offway.core.itinerary.repository.TripOutcomeRepository;
import com.offway.core.itinerary.service.dto.MyCourses;
import com.offway.core.itinerary.service.dto.PendingTrips;
import com.offway.core.leave.service.LeaveService;
import com.offway.core.leave.service.MyLeaveService;
import com.offway.core.leave.service.dto.AvailableTimeCommand;
import com.offway.core.leave.service.dto.MyLeave;
import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 홈 진입 모달 "다녀오셨나요?"(#116) — 지난 여행을 묻고, 답에 따라 연차를 깎는다.
 *
 * <p><b>왜 홈에서 묻나.</b> 원안은 내 코스 → 카드 → "연차 차감하기" 를 사용자가 스스로 찾아 눌러야 했다. 다녀온
 * 직후에 그 화면에 들어갈 이유가 없으니 대부분 누르지 않고, 연차 잔액은 계속 틀린 채로 남는다. 차감 시점은
 * <b>"여행이 끝났다" 는 사실</b>이 정해주므로, 그 사실을 아는 쪽이 먼저 묻는다. 카드의 버튼은 그대로 두고
 * ({@link CourseLeaveDeductionService}), 놓친 사람을 홈에서 한 번 더 잡는다.
 *
 * <p><b>트랜잭션을 걸지 않는다.</b> 차감 일수 계산이 공휴일(외부) 조회를 탄다. 코스 조회와 기록은 각자의 짧은
 * 트랜잭션에서 끝난다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripOutcomeService {

    private final CourseRepository courseRepository;
    private final CourseStorageService courseStorageService;
    private final CourseLeaveDeductionService leaveDeductionService;
    private final TripOutcomeRepository tripOutcomeRepository;
    private final RegionRepository regionRepository;
    private final LeaveService leaveService;
    private final MyLeaveService myLeaveService;

    /**
     * 물어볼 지난 여행들 — <b>여행이 끝났고</b>(종료일 &lt; 오늘) <b>아직 답하지 않은</b> 코스.
     *
     * <p>종료 당일은 넣지 않는다. 아직 여행 중일 수 있다.
     *
     * <p>이미 차감한 코스도 빠진다 — 내 코스 카드에서 "연차 차감하기" 를 눌렀다면 그게 곧 "다녀왔다" 는 답이다.
     *
     * <p>여행 날짜 없이 저장된 코스는 애초에 {@link CourseScope#PAST} 에 들어오지 않는다. 지났는지 알 수 없다.
     */
    public PendingTrips pending(UUID userId) {
        // 페이지가 아니라 전부 — 여기서 더 거르므로(끝났나·답했나·차감했나) 잘라 오면 답이 달라진다.
        MyCourses past = courseStorageService.allCourses(userId, CourseScope.PAST);
        Set<Long> answered = tripOutcomeRepository.findAnsweredCourseIds(userId);

        List<Course> waiting = past.courses().stream()
                .filter(course -> course.hasEndedBy(past.today()))
                .filter(course -> !answered.contains(course.getId()))
                .filter(course -> !past.isDeducted(course))
                .toList();

        Double remaining = myLeaveService.remainingDaysOrNull(userId);
        if (waiting.isEmpty()) {
            return new PendingTrips(List.of(), Map.of(), Map.of(), remaining);
        }
        return new PendingTrips(waiting, regionNamesOf(waiting), consumedLeaveDaysOf(waiting), remaining);
    }

    /**
     * "다녀오셨나요?" 에 답한다.
     *
     * <p><b>차감을 먼저 하고 답을 기록한다.</b> 순서가 반대면, 기록은 됐는데 차감이 실패했을 때 다시 묻지 않으므로
     * 연차가 영영 안 깎인다. 이 순서면 기록이 실패해도 다음에 다시 묻고, 차감은 멱등이라 두 번 깎이지 않는다.
     *
     * @return 답한 뒤의 내 연차 — 모달이 상단 "남은 연차" 를 바로 고쳐 그린다
     */
    public MyLeave answer(UUID userId, long courseId, VisitOutcome outcome) {
        Course course = findOwned(userId, courseId);
        course.requireTravelDate();
        requireAnswerable(userId, course);

        if (outcome.deductsLeave()) {
            // 단위를 여기서 정하지 않는다 — 코스가 만들어질 때 이미 답한 값이다(#284·#288).
            // 모달이 반차를 안 묻는 것은 맞지만, 그게 "종일로 친다" 는 뜻은 아니었다. 물을 필요가 없을 뿐이다.
            leaveDeductionService.deduct(userId, courseId, course.startDayLeave());
        }

        try {
            tripOutcomeRepository.save(
                    TripOutcome.of(userId, courseId, outcome, LocalDate.now(CourseStorageService.SERVICE_ZONE)));
        } catch (DataIntegrityViolationException e) {
            // 모달을 두 번 눌렀다 — 유니크 제약이 두 번째를 막았다. 앞선 답이 이미 남아 있으므로 409 가 맞다.
            log.info("여행 결과 동시 제출 — 먼저 기록된 답을 그대로 둡니다 courseId={}", courseId);
            throw ItineraryException.tripAlreadyAnswered();
        }

        log.info("여행 결과 기록 courseId={} outcome={}", courseId, outcome);
        return myLeaveService.myLeave(userId);
    }

    /**
     * 답할 수 있는 여행인가 — <b>{@link #pending} 이 물어봤을 법한 여행만</b> 답을 받는다.
     *
     * <p>조회 조건과 쓰기 조건이 어긋나면 모달이 묻지 않은 것에도 답이 들어온다. 그 결과가 가볍지 않다:
     * <ul>
     *   <li>아직 시작도 안 한 여행에 {@code VISITED} → 다녀오지 않았는데 연차가 깎인다.</li>
     *   <li>이미 차감한 여행에 {@code NOT_VISITED} → 차감은 남은 채 "안 갔다" 로 기록되고, 모순된 상태인데
     *       모달에도 다시 뜨지 않아 화면에서 바로잡을 길이 사라진다.</li>
     * </ul>
     *
     * <p>이미 차감한 코스를 {@code TRIP_ALREADY_ANSWERED} 로 막는 이유 — 내 코스 카드에서 "연차 차감하기" 를
     * 눌렀다면 <b>그게 곧 "다녀왔다" 는 답</b>이다({@link #pending} 이 같은 이유로 걸러낸다).
     */
    private void requireAnswerable(UUID userId, Course course) {
        if (!course.hasEndedBy(LocalDate.now(CourseStorageService.SERVICE_ZONE))) {
            throw ItineraryException.tripNotEnded();
        }
        if (tripOutcomeRepository.findAnsweredCourseIds(userId).contains(course.getId())) {
            throw ItineraryException.tripAlreadyAnswered();
        }
        if (myLeaveService.alreadyDeducted(userId, course.getId())) {
            throw ItineraryException.tripAlreadyAnswered();
        }
    }

    /** 지역명을 한 번에 — 코스마다 조회하면 N+1 이다. */
    private Map<Long, String> regionNamesOf(List<Course> courses) {
        List<Long> ids = courses.stream().map(Course::getRegionId).distinct().toList();
        return regionRepository.findByIds(ids).stream()
                .collect(Collectors.toMap(Region::getId, Region::getSigungu, (first, second) -> first));
    }

    /**
     * 코스별로 깎일 연차 일수 — 모달의 "연차 N일 차감".
     *
     * <p>공휴일 조회가 외부 호출이라 순차로 돌면 여행 수만큼 곱해진다. 다만 밀린 여행은 많아야 몇 건이고, 대개
     * <b>같은 달을 반복해서 묻는</b> 구조라 첫 조회 뒤에는 {@link LeaveService} 의 월 단위 캐시에서 나온다.
     */
    private Map<Long, Double> consumedLeaveDaysOf(List<Course> courses) {
        Map<Long, Double> byCourse = new LinkedHashMap<>();
        for (Course course : courses) {
            byCourse.put(course.getId(), leaveService
                    .calculate(new AvailableTimeCommand.FixedDates(
                            course.getTravelDate(),
                            course.travelEndDate(),
                            course.getTransport(),
                            // 표시와 실제 차감이 같은 단위를 써야 한다. 여기만 종일로 두면 모달이
                            // "1일 차감" 이라 적고 실제로는 0.5 를 깎아, 사용자가 본 숫자와 결과가 어긋난다.
                            course.startDayLeave()))
                    .availableTime()
                    .consumedLeaveDays());
        }
        return byCourse;
    }

    /** 조회와 같은 규칙 — 없거나 남의 코스면 404. 존재 여부를 알려주지 않으려 403 으로 나누지 않는다. */
    private Course findOwned(UUID userId, long courseId) {
        return courseRepository
                .findByIdAndUserId(courseId, userId)
                .orElseThrow(ItineraryException::courseNotFound);
    }
}
