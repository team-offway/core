package com.offway.core.itinerary.service;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.ItineraryException;
import com.offway.core.itinerary.repository.CourseRepository;
import com.offway.core.leave.service.LeaveService;
import com.offway.core.leave.service.MyLeaveService;
import com.offway.core.leave.domain.StartDayLeave;
import com.offway.core.leave.service.dto.AddLeaveUsage;
import com.offway.core.leave.service.dto.AvailableTimeCommand;
import com.offway.core.leave.service.dto.MyLeave;
import java.time.LocalDate;
import java.util.OptionalDouble;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 코스 확정 → 연차 차감(#91). 와이어프레임의 "연차를 차감할까요?" 확인에 대응하는 명시적 액션이다.
 *
 * <p><b>차감 일수는 서버가 다시 계산한다.</b> 클라이언트가 보낸 일수를 믿으면 임의 차감이 되고, 차감 시점에 날짜를
 * 받아도 마찬가지다 — 날짜를 늘려 보내면 그만큼 더 깎인다. 그래서 저장된 코스의 여행 날짜를 정본으로 삼아
 * {@link LeaveService} 가 평일−공휴일(반차 0.5)을 계산한다.
 *
 * <p><b>잔여가 부족해도 막지 않는다</b>(결정 #38). 프론트가 경고하고 사용자가 확인하면 진행하며, 서버는 음수 잔여를
 * 허용한다.
 *
 * <p><b>트랜잭션을 걸지 않는다.</b> 공휴일 조회가 외부 호출이라 트랜잭션 안에 넣으면 read-timeout 동안 DB 커넥션을
 * 잡는다. 코스 조회와 내역 기록은 각자의 짧은 트랜잭션에서 끝난다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseLeaveDeductionService {

    /** 사용 내역에 남길 사유. 사용자가 내역에서 보는 문구다. */
    private static final String DEDUCTION_REASON = "코스 확정";

    private final CourseRepository courseRepository;
    private final LeaveService leaveService;
    private final MyLeaveService myLeaveService;

    /**
     * 코스로 연차를 차감하고 차감 뒤의 내 연차를 준다.
     *
     * <p><b>멱등하다</b> — 같은 코스로 다시 확정해도 내역이 늘지 않고 현재 상태를 그대로 돌려준다. 확인은 두 겹이다:
     * 먼저 조회로 거르고(대부분의 재확정), 동시 요청은 유니크 제약이 막는다. 조회만으로는 두 요청이 <b>둘 다</b>
     * "아직 안 했다" 를 보고 두 번 차감한다.
     *
     * @param startDayLeave 첫날에 쓴 연차 — 차감이 그 단위만큼 줄어든다(반차 0.5 · 반반차 0.25)
     */
    public MyLeave deduct(String guestId, long courseId, StartDayLeave startDayLeave) {
        Course course = findOwned(guestId, courseId);
        course.requireTravelDate();

        if (myLeaveService.alreadyDeducted(guestId, courseId)) {
            log.info("코스 연차 차감 건너뜀 — 이미 차감된 코스 courseId={}", courseId);
            return myLeaveService.myLeave(guestId);
        }

        double days = consumedLeaveDays(course, startDayLeave);
        try {
            MyLeave after = myLeaveService.addUsage(
                    guestId,
                    // 메모는 없다(#319) — 사용자가 쓰는 칸이고, 이 행은 서버가 만든다.
                    new AddLeaveUsage(
                            course.getTravelDate(), days, DEDUCTION_REASON, null, courseId, startDayLeave));
            log.info("코스 연차 차감 courseId={} days={} 남은={}", courseId, days, after.summary().remainingDays());
            return after;
        } catch (DataIntegrityViolationException e) {
            // 이 예외는 유니크 제약 위반만 뜻하지 않는다(NOT NULL·길이 초과 등). 정말 먼저 기록된 내역이 있을 때만
            // '이미 차감됨' 과 결과가 같다. 확인 없이 삼키면 아무것도 저장되지 않았는데 200 을 주게 되고,
            // 사용자와 화면은 차감이 끝난 줄 안다 — 규약이 막는 '조용한 실패' 그 자체다.
            if (!myLeaveService.alreadyDeducted(guestId, courseId)) {
                log.error("코스 연차 차감 실패 — 중복이 아닌 제약 위반 courseId={}", courseId, e);
                throw e;
            }
            log.info("코스 연차 차감 경합 — 먼저 기록된 내역을 그대로 둡니다 courseId={}", courseId);
            return myLeaveService.myLeave(guestId);
        }
    }

    /**
     * 차감을 되돌린다(#113). 코스는 그대로 두고 연차만 복구한다.
     *
     * <p><b>멱등하다</b> — 차감된 적이 없어도 조용히 현재 상태를 준다. 여기서 404 를 주면 화면이 "취소 실패" 를
     * 보여주는데, 사용자가 원한 상태(차감 없음)는 이미 이뤄져 있다.
     *
     * <p>코스 소유 확인은 그대로 한다 — 남의 코스 ID 로 남의 연차를 건드릴 수 있으면 안 된다.
     */
    public MyLeave cancel(String guestId, long courseId) {
        findOwned(guestId, courseId);
        myLeaveService.cancelCourseDeduction(guestId, courseId);
        return myLeaveService.myLeave(guestId);
    }

    /**
     * 차감 일수를 계산한다 — 코스의 여행 구간에 대해 평일에서 공휴일을 뺀 값(반차 0.5).
     *
     * <p>{@link LeaveService} 가 공휴일(특일정보)을 조회하므로 <b>외부 호출이다</b>. 실패해도 그쪽이 폴백을 갖는다.
     */
    private double consumedLeaveDays(Course course, StartDayLeave startDayLeave) {
        return consumedLeaveDays(course.getTravelDate(), course.travelEndDate(), course, startDayLeave);
    }

    /**
     * 여행 날짜가 바뀐 코스의 차감 일수를 다시 계산한다(#170) — <b>이미 차감한 코스만</b>.
     *
     * <p>차감한 적 없는 코스에는 계산도 하지 않는다. 공휴일 조회는 외부 호출이라, 아무 데도 반영되지 않을
     * 값을 구하려고 특일정보를 부르는 셈이 된다.
     *
     * <p><b>반차 여부는 기존 내역에서 가져온다.</b> 사용자가 확정할 때 고른 값이고 날짜를 고쳤다고 바뀌지
     * 않는다. 지금 깎인 일수에서는 되짚을 수 없어(출발일이 주말이면 반차여도 정수) 내역이 직접 들고 있다.
     *
     * <p>{@code 0} 이 나올 수 있다 — 주말·공휴일로만 이뤄진 구간이다. 그 처리는
     * {@link MyLeaveService#rescheduleCourseDeduction} 이 소유한다.
     *
     * @param travelDate 옮겨갈 여행 시작일
     * @return 새 차감 일수. 이 코스로 차감한 적이 없으면 empty
     */
    public OptionalDouble recalculateFor(Course course, LocalDate travelDate) {
        return myLeaveService
                .courseDeduction(course.getGuestId(), course.getId())
                .map(deduction -> OptionalDouble.of(consumedLeaveDays(
                        travelDate,
                        travelDate.plusDays(course.getTravelDays() - 1L),
                        course,
                        deduction.startDayLeave())))
                .orElseGet(OptionalDouble::empty);
    }

    private double consumedLeaveDays(
            LocalDate start, LocalDate end, Course course, StartDayLeave startDayLeave) {
        return leaveService
                .calculate(new AvailableTimeCommand.FixedDates(start, end, course.getTransport(), startDayLeave))
                .availableTime()
                .consumedLeaveDays();
    }

    /**
     * 조회와 같은 규칙 — 없거나 남의 코스면 404. 존재 여부를 알려주지 않으려 403 으로 나누지 않는다.
     *
     * <p>트랜잭션을 걸지 않는다. 같은 빈 안에서 부르면 프록시를 거치지 않아 어차피 무효고, 리포지토리 호출 하나라
     * 그것으로 충분하다. 지연 로딩 컬렉션({@code days})은 여기서 건드리지 않는다 — 날짜·일수·이동수단만 쓴다.
     */
    private Course findOwned(String guestId, long courseId) {
        return courseRepository
                .findByIdAndGuestId(courseId, guestId)
                .orElseThrow(ItineraryException::courseNotFound);
    }
}
