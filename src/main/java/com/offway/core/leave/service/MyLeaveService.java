package com.offway.core.leave.service;

import com.offway.core.leave.domain.LeaveBalance;
import com.offway.core.leave.domain.LeaveSummary;
import com.offway.core.leave.domain.LeaveUsage;
import com.offway.core.leave.repository.LeaveBalanceRepository;
import com.offway.core.leave.repository.LeaveUsageRepository;
import com.offway.core.leave.service.dto.AddLeaveUsage;
import com.offway.core.leave.service.dto.MyLeave;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "내 연차" 조율 — 총 연차 저장·수정과 사용 내역. 외부 호출이 없고 DB 만 만지므로 트랜잭션 경계를 갖는다
 * ({@link LeaveService} 와 반대다 — 그쪽은 특일정보 외부 호출이라 트랜잭션이 없다).
 *
 * <p>남은 연차는 저장하지 않고 {@link LeaveSummary} 가 파생한다 — 사용 내역이 정본이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyLeaveService {

    /** 아직 연차를 설정하지 않은 소유자의 총 연차. 0 이면 "쓸 수 있는 게 없다" 가 아니라 "아직 안 넣었다" 는 뜻이다. */
    private static final double UNSET_TOTAL_DAYS = 0;

    private final LeaveBalanceRepository balanceRepository;
    private final LeaveUsageRepository usageRepository;

    /** 내 연차 현황 + 사용 내역. 아직 아무것도 없으면 총 0·내역 없음으로 답한다(404 가 아니다). */
    public MyLeave myLeave(String guestId) {
        return new MyLeave(summaryOf(guestId), usageRepository.findByGuestId(guestId));
    }

    /** 총 연차를 고쳐 쓴다(와이어프레임 +/- 스테퍼). 없으면 만든다. */
    @Transactional
    public MyLeave changeTotalDays(String guestId, double totalDays) {
        LeaveBalance balance = balanceRepository.findByGuestId(guestId)
                .map(existing -> {
                    existing.changeTotal(totalDays);
                    return existing;
                })
                .orElseGet(() -> LeaveBalance.of(guestId, totalDays));
        balanceRepository.save(balance);
        log.info("연차 총량 변경 total={}", totalDays);
        return myLeave(guestId);
    }

    /**
     * 사용 내역을 추가한다. 총 연차가 아직 없으면 함께 만든다 — 내역만 있고 총량이 없으면 남은 연차가 음수로 보인다.
     *
     * <p><b>남은 연차가 부족해도 막지 않는다</b>(결정 #38). 프론트가 경고하고 사용자가 확인하면 진행한다.
     */
    @Transactional
    public MyLeave addUsage(String guestId, AddLeaveUsage command) {
        ensureBalance(guestId);
        LeaveUsage usage = command.courseId() == null
                ? LeaveUsage.manual(guestId, command.usedOn(), command.days(), command.reason())
                : LeaveUsage.forCourse(
                        guestId, command.usedOn(), command.days(), command.reason(), command.courseId());
        usageRepository.save(usage);
        MyLeave after = myLeave(guestId);
        log.info("연차 사용내역 추가 days={} 남은={}", command.days(), after.summary().remainingDays());
        return after;
    }

    /** 홈 배지가 쓰는 남은 연차. 설정한 적이 없으면 {@code null} — 0 과 구분해야 화면이 "미설정" 을 보여줄 수 있다. */
    public Double remainingDaysOrNull(String guestId) {
        if (guestId == null || guestId.isBlank()) {
            return null;
        }
        return balanceRepository.findByGuestId(guestId)
                .map(balance -> new LeaveSummary(balance.getTotalDays(), usageRepository.sumDaysByGuestId(guestId))
                        .remainingDays())
                .orElse(null);
    }

    /** 코스로 이미 차감했는지 — 중복 차감 방지(#91). */
    public boolean alreadyDeducted(String guestId, long courseId) {
        return usageRepository.existsByGuestIdAndCourseId(guestId, courseId);
    }

    private LeaveSummary summaryOf(String guestId) {
        double total = balanceRepository.findByGuestId(guestId)
                .map(LeaveBalance::getTotalDays)
                .orElse(UNSET_TOTAL_DAYS);
        return new LeaveSummary(total, usageRepository.sumDaysByGuestId(guestId));
    }

    private void ensureBalance(String guestId) {
        if (balanceRepository.findByGuestId(guestId).isEmpty()) {
            balanceRepository.save(LeaveBalance.of(guestId, UNSET_TOTAL_DAYS));
        }
    }
}
