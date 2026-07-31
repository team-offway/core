package com.offway.core.leave.service;

import com.offway.core.leave.domain.LeaveBalance;
import com.offway.core.leave.domain.LeaveException;
import com.offway.core.leave.domain.LeaveSummary;
import com.offway.core.leave.domain.LeaveUsage;
import com.offway.core.leave.repository.LeaveBalanceRepository;
import com.offway.core.leave.repository.LeaveUsageRepository;
import com.offway.core.leave.service.dto.AddLeaveUsage;
import com.offway.core.leave.service.dto.MyLeave;
import java.util.Set;
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
 *
 * <p><b>클래스 레벨에 트랜잭션을 걸지 않는다.</b> 걸면 {@link #changeTotalDays} 까지 바깥 트랜잭션에 묶여, 동시 생성
 * 제약 위반이 그 트랜잭션을 rollback-only 로 찍고 커밋 시점에 {@code UnexpectedRollbackException} 으로 끝난다.
 * 읽기 메서드에만 개별로 건다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MyLeaveService {

    /** 아직 연차를 설정하지 않은 소유자의 총 연차. 0 이면 "쓸 수 있는 게 없다" 가 아니라 "아직 안 넣었다" 는 뜻이다. */
    private static final double UNSET_TOTAL_DAYS = 0;

    private final LeaveBalanceRepository balanceRepository;
    private final LeaveUsageRepository usageRepository;
    private final MyLeavePersistenceService persistenceService;

    /** 내 연차 현황 + 사용 내역. 아직 아무것도 없으면 총 0·내역 없음으로 답한다(404 가 아니다). */
    @Transactional(readOnly = true)
    public MyLeave myLeave(String guestId) {
        String owner = requireOwner(guestId);
        return new MyLeave(summaryOf(owner), usageRepository.findByGuestId(owner));
    }

    /**
     * 총 연차를 고쳐 쓴다(와이어프레임 +/- 스테퍼). 없으면 만든다.
     *
     * <p><b>이 메서드에 트랜잭션을 걸지 않는다.</b> 동시 생성 경합을 한 트랜잭션 안에서 잡아 이어가면, 제약 위반 뒤
     * Hibernate 세션이 못 쓸 상태가 돼 커밋 시점에 {@code UnexpectedRollbackException} 으로 끝날 수 있다.
     * 각 시도를 {@link MyLeavePersistenceService} 의 <b>독립 트랜잭션</b>으로 두면 실패한 삽입은 깔끔히
     * 롤백되고 재시도는 새 트랜잭션에서 돈다.
     */
    public MyLeave changeTotalDays(String guestId, double totalDays) {
        String owner = requireOwner(guestId);
        if (!persistenceService.updateTotalIfPresent(owner, totalDays)) {
            try {
                persistenceService.create(owner, totalDays);
            } catch (DataIntegrityViolationException e) {
                // 경쟁에서 졌다 — '먼저 넣은 쪽이 이겼다' 는 뜻일 뿐이라 실패가 아니다.
                // 이긴 행에 내 변경을 다시 적용한다. 그냥 무시하면 사용자가 누른 값이 사라진다.
                if (!persistenceService.updateTotalIfPresent(owner, totalDays)) {
                    throw new IllegalStateException("유니크 제약에 걸렸는데 행이 없습니다: " + owner, e);
                }
                log.info("연차 잔액 동시 생성 — 먼저 만들어진 행에 이어서 적용했습니다");
            }
        }
        log.info("연차 총량 변경 total={}", totalDays);
        return myLeave(owner);
    }

    /**
     * 사용 내역을 추가한다.
     *
     * <p><b>잔액 행을 만들지 않는다.</b> {@link #summaryOf} 가 잔액이 없을 때 총 {@value #UNSET_TOTAL_DAYS} 로
     * 답하므로, 총 0 인 행을 미리 만들어도 결과가 같다 — 얻는 것 없이 동시 생성 경합만 한 자리 더 만든다.
     *
     * <p><b>남은 연차가 부족해도 막지 않는다</b>(결정 #38). 프론트가 경고하고 사용자가 확인하면 진행한다.
     */
    @Transactional
    public MyLeave addUsage(String guestId, AddLeaveUsage command) {
        String owner = requireOwner(guestId);
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
    @Transactional(readOnly = true)
    public Double remainingDaysOrNull(String guestId) {
        if (guestId == null || guestId.isBlank()) {
            return null;
        }
        return balanceRepository.findByGuestId(guestId)
                .map(balance -> new LeaveSummary(balance.getTotalDays(), usageRepository.sumDaysByGuestId(guestId))
                        .remainingDays())
                .orElse(null);
    }

    /**
     * 코스 차감을 되돌린다(#113). 코스는 그대로 두고 연차만 복구한다.
     *
     * <p><b>멱등하다</b> — 차감된 적이 없어도 조용히 넘어간다. "취소했다" 와 "원래 없었다" 는 사용자에게 같은 결과다.
     *
     * <p>취소는 음수 행을 덧붙이지 않고 <b>그 행을 지운다</b>. {@code uk_leave_usage_guest_course} 가 코스당 한 행을
     * 강제하므로(#91) 음수 누적은 수동 내역 전용이다.
     *
     * <p>기본 전파라 <b>호출자의 트랜잭션에 합류한다</b> — 코스 삭제와 한 덩어리로 묶여야 "코스만 사라지고 연차는
     * 깎인 채" 가 남지 않는다.
     *
     * @return 되돌린 내역이 있었으면 {@code true}
     */
    @Transactional
    public boolean cancelCourseDeduction(String guestId, long courseId) {
        String owner = requireOwner(guestId);
        int removed = usageRepository.deleteByGuestIdAndCourseId(owner, courseId);
        if (removed > 0) {
            log.info("코스 연차 차감 취소 courseId={} 되돌린내역={}", courseId, removed);
        }
        return removed > 0;
    }

    /**
     * 이 소유자가 차감한 코스 ID 들 — 목록 화면의 "차감함" 표시용.
     *
     * <p>코스마다 {@link #alreadyDeducted} 를 부르면 코스 수만큼 쿼리가 늘어난다(N+1). 한 번에 모아온다.
     */
    @Transactional(readOnly = true)
    public Set<Long> deductedCourseIds(String guestId) {
        return usageRepository.findDeductedCourseIds(requireOwner(guestId));
    }

    /** 코스로 이미 차감했는지 — 중복 차감 방지(#91). */
    @Transactional(readOnly = true)
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
