package com.offway.core.leave.event;

import com.offway.core.leave.repository.LeaveBalanceRepository;
import com.offway.core.leave.repository.LeaveUsageRepository;
import com.offway.core.user.event.UserWithdrawn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 탈퇴하면 그 사람의 연차 설정·사용 내역을 지운다. {@code leave} 가 자기 테이블을 스스로 치운다.
 *
 * <p>연차는 "며칠 남았고 언제 썼는가" 라 근무 이력에 가까운 개인정보다. 계정이 사라지면 되짚을 사람도
 * 없으므로 남길 이유가 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeavePurgeOnUserWithdrawn {

    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveUsageRepository leaveUsageRepository;

    @EventListener
    public void on(UserWithdrawn event) {
        event.guestIdIfPresent().ifPresentOrElse(this::purge, () -> logSkipped(event));
    }

    private void purge(String guestId) {
        int usages = leaveUsageRepository.deleteByGuestId(guestId);
        int balances = leaveBalanceRepository.deleteByGuestId(guestId);
        log.info("탈퇴 정리 — 연차 내역 {}건 · 연차 설정 {}건 삭제", usages, balances);
    }

    /** 지우지 못하고 넘어간 것을 반드시 남긴다 — 개인정보 삭제에서 조용한 실패는 그대로 사고다. */
    private void logSkipped(UserWithdrawn event) {
        log.warn("탈퇴 정리 건너뜀 — 게스트 식별자가 없어 연차 데이터에 닿지 못했다 userId={}", event.userId());
    }
}
