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

    /**
     * <b>건너뛰는 길이 없다</b>(#280). 예전에는 연차가 게스트 키로 묶여 있어, 로그인 때 기기를 이어 두지
     * 못했으면 지울 대상을 못 찾고 warn 만 남겼다. 이제 소유 키가 탈퇴한 사용자 본인이라 항상 닿는다.
     */
    @EventListener
    public void on(UserWithdrawn event) {
        int usages = leaveUsageRepository.deleteByUserId(event.userId());
        int balances = leaveBalanceRepository.deleteByUserId(event.userId());
        log.info("탈퇴 정리 — 연차 내역 {}건 · 연차 설정 {}건 삭제", usages, balances);
    }
}
