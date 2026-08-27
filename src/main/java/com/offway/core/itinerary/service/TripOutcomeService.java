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
import org.springframework.transaction.annotation.Transactional;

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
     * <p>이미 차감한 코스도 빠진다. 차감은 {@code VISITED} 로 답할 때만 생기므로(#288) 차감이 있다는 것은
     * 곧 답이 있다는 뜻이고, 위 조건에 이미 걸린다 — 겹쳐서 거르는 셈이라 그대로 둔다.
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
     * 그 날 여행이 끝났는데 <b>아직 답을 안 한</b> 코스들 — 종료 다음 날 알림 배치가 물어볼 대상(#302).
     *
     * <p><b>{@link #pending} 과 같은 조건을 쓴다.</b> 조회 조건이 갈리면 알림은 갔는데 눌러 들어가면 모달이
     * 안 뜨는 헛걸음이 생긴다. 다른 것은 범위뿐이다 — {@code pending} 은 한 사람의 <b>지난 여행 전부</b>를,
     * 여기서는 <b>그 날 끝난 것</b>만 본다.
     *
     * <p><b>지난 것 전부가 아니라 그 날 끝난 것만 보는 이유.</b> 전부로 잡으면 첫 배포에 과거 미답 코스가
     * 한꺼번에 알림·푸시로 나간다 — 몇 달 전 여행까지 함께. 대가는 배치가 하루 거르면 그 코스는 알림을 못
     * 받는다는 것인데, 모달(#116)이 그대로 뜨므로 답할 길이 사라지지는 않는다.
     *
     * <p><b>차감한 코스도 뺀다.</b> 차감은 {@code VISITED} 로 답할 때만 생기므로(#288) 답이 있다는 뜻이다 —
     * {@code pending} 이 같은 이유로 걸러낸다.
     *
     * <p>소유자별로 묻지 않는다. 대상마다 "답했나·차감했나" 를 물으면 그게 곧 N+1 이라, 코스 id 를 모아
     * 두 번의 질의로 끝낸다.
     *
     * <p><b>이 메서드에만 트랜잭션이 붙는 이유.</b> 이 클래스는 차감 계산이 공휴일(외부) 조회를 타서 트랜잭션을
     * 걸지 않는다. 여기는 외부를 부르지 않는 <b>읽기 셋</b>이라 그 사정이 없고, 한 스냅샷으로 묶어야 "끝난
     * 코스" 를 읽은 뒤 "답했나" 를 읽는 사이에 답이 들어와도 판단이 흔들리지 않는다.
     */
    @Transactional(readOnly = true)
    public List<Course> unansweredTripsEndedOn(LocalDate endedOn) {
        List<Course> ended = courseRepository.findEndedOn(endedOn);
        if (ended.isEmpty()) {
            // 0건이 "그날 끝난 여행이 없다" 인지 "있는데 못 잡았다" 인지는 이 로그가 유일한 단서다.
            // 알림이 안 왔다는 제보를 받고도 세 갈래(안 돌았다·못 잡았다·걸러졌다)를 못 갈랐다(#309).
            log.info("여행 종료 대상 — 그 날 끝난 코스가 없습니다 endedOn={}", endedOn);
            return List.of();
        }
        List<Long> courseIds = ended.stream().map(Course::getId).toList();
        Set<Long> answered = tripOutcomeRepository.findAnsweredCourseIdsIn(courseIds);
        Set<Long> deducted = myLeaveService.deductedCourseIdsIn(courseIds);

        List<Course> waiting = ended.stream()
                .filter(course -> !answered.contains(course.getId()))
                .filter(course -> !deducted.contains(course.getId()))
                .toList();
        // 넷을 함께 남겨야 "후보가 없었다" 와 "후보는 있었는데 전부 걸러졌다" 가 로그만으로 갈린다.
        log.info("여행 종료 대상 endedOn={} 끝난 코스={}건 이미 답함={}건 이미 차감={}건 남은 대상={}건",
                endedOn, ended.size(), answered.size(), deducted.size(), waiting.size());
        return waiting;
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
     * <p>마지막 조건(이미 차감함)은 <b>지금 계약으로는 닿지 않는다.</b> 차감은 {@code VISITED} 로 답할 때만
     * 생기고(#288), 차감을 취소하면 답변도 함께 지워진다(#328) — 즉 "차감은 있는데 답은 없는" 상태를 만들
     * 길이 없다. 그래도 남긴다: 둘 중 하나만 지우는 경로가 생기면 그때 이 가드가 <b>이중 차감을 막는
     * 마지막 자리</b>다. 지워도 당장은 아무 테스트도 깨지지 않기 때문에 더더욱 남긴다.
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
