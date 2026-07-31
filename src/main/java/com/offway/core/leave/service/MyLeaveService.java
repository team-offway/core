package com.offway.core.leave.service;

import com.offway.core.leave.domain.LeaveBalance;
import com.offway.core.leave.domain.LeaveException;
import com.offway.core.leave.domain.LeaveSummary;
import com.offway.core.leave.domain.LeaveUsage;
import com.offway.core.leave.repository.LeaveBalanceRepository;
import com.offway.core.leave.repository.LeaveUsageRepository;
import com.offway.core.leave.service.dto.AddLeaveUsage;
import com.offway.core.leave.service.dto.MyLeave;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
        String owner = requireOwner(guestId);
        return new MyLeave(summaryOf(owner), usageRepository.findByGuestId(owner));
    }

    /** 총 연차를 고쳐 쓴다(와이어프레임 +/- 스테퍼). 없으면 만든다. */
    @Transactional
    public MyLeave changeTotalDays(String guestId, double totalDays) {
        String owner = requireOwner(guestId);
        upsertBalance(owner, balance -> balance.changeTotal(totalDays), () -> LeaveBalance.of(owner, totalDays));
        log.info("연차 총량 변경 total={}", totalDays);
        return myLeave(owner);
    }

    /**
     * 사용 내역을 추가한다. 총 연차가 아직 없으면 함께 만든다 — 내역만 있고 총량이 없으면 남은 연차가 음수로 보인다.
     *
     * <p><b>남은 연차가 부족해도 막지 않는다</b>(결정 #38). 프론트가 경고하고 사용자가 확인하면 진행한다.
     */
    @Transactional
    public MyLeave addUsage(String guestId, AddLeaveUsage command) {
        String owner = requireOwner(guestId);
        upsertBalance(owner, balance -> { }, () -> LeaveBalance.of(owner, UNSET_TOTAL_DAYS));
        LeaveUsage usage = command.courseId() == null
                ? LeaveUsage.manual(owner, command.usedOn(), command.days(), command.reason())
                : LeaveUsage.forCourse(
                        owner, command.usedOn(), command.days(), command.reason(), command.courseId());
        usageRepository.save(usage);
        MyLeave after = myLeave(owner);
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

    /**
     * 있으면 고치고 없으면 만든다 — <b>동시 생성 경합을 여기 한 곳에서 처리</b>한다.
     *
     * <p>"조회 후 없으면 생성" 은 같은 소유자로 요청이 겹치면(더블클릭·클라이언트 재시도) 둘 다 "없음" 을 보고 각자
     * 만들려 든다. 한쪽이 {@code uk_leave_balance_guest} 에 걸려 {@link DataIntegrityViolationException} 으로
     * 튕기는데, 이건 클라이언트 잘못이 아니라 <b>먼저 넣은 쪽이 이겼다는 뜻</b>일 뿐이다.
     *
     * <p>그래서 잡고 재조회해 다시 적용한다. 유니크 제약이 있으니 재조회는 반드시 성공한다 — 그래도 없으면 제약이
     * 사라진 것이므로 불변식 위반으로 드러낸다.
     *
     * @param onExisting 이미 있을 때 적용할 변경 (사용 내역 추가처럼 총량을 안 건드리면 no-op)
     * @param toCreate 없을 때 만들 새 값
     */
    private void upsertBalance(String guestId, Consumer<LeaveBalance> onExisting, Supplier<LeaveBalance> toCreate) {
        Optional<LeaveBalance> found = balanceRepository.findByGuestId(guestId);
        if (found.isPresent()) {
            LeaveBalance balance = found.get();
            onExisting.accept(balance);
            balanceRepository.save(balance);
            return;
        }
        try {
            balanceRepository.save(toCreate.get());
        } catch (DataIntegrityViolationException e) {
            LeaveBalance winner = balanceRepository.findByGuestId(guestId)
                    .orElseThrow(() -> new IllegalStateException("유니크 제약에 걸렸는데 행이 없습니다: " + guestId, e));
            log.info("연차 잔액 동시 생성 — 먼저 만들어진 행에 이어서 적용합니다");
            onExisting.accept(winner);
            balanceRepository.save(winner);
        }
    }

    /**
     * 소유 키 계약 검증. 빈 헤더({@code X-Guest-Id: " "})는 {@code @RequestHeader} 를 통과하므로 <b>멀쩡한
     * 클라이언트가 정상 요청으로 닿을 수 있다</b> — 도메인까지 흘려보내면 500 이 나간다.
     *
     * <p>조회에도 건다. 조회만 실패하지 않는다고 통과시키면 같은 요청이 메서드에 따라 200 과 400 으로 갈린다.
     */
    private static String requireOwner(String guestId) {
        if (guestId == null || guestId.isBlank() || guestId.length() > LeaveBalance.MAX_OWNER_ID_LENGTH) {
            throw LeaveException.invalidOwnerId();
        }
        return guestId;
    }
}
