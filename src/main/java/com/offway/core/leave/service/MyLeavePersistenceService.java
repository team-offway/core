package com.offway.core.leave.service;

import com.offway.core.leave.domain.LeaveBalance;
import com.offway.core.leave.repository.LeaveBalanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연차 잔액 영속만 담당하는 별도 빈 — <b>트랜잭션 경계를 쪼개려고</b> 분리했다.
 *
 * <p>동시 생성 경합을 한 트랜잭션 안에서 잡아 이어가면 안 된다. 제약 위반이 나면 Hibernate 세션이 더 못 쓸 상태가 되고,
 * 예외를 삼켜도 커밋 시점에 {@code UnexpectedRollbackException} 으로 끝날 수 있다. 각 시도를 <b>독립 트랜잭션</b>으로
 * 두면 실패한 삽입은 깔끔히 롤백되고, 재시도는 새 트랜잭션에서 돈다.
 *
 * <p>같은 빈 안에서 나눠도 소용없다 — self-invocation 은 프록시를 거치지 않아 트랜잭션이 갈리지 않는다
 * (persistence-convention). 그래서 빈을 분리한다.
 */
@Service
@RequiredArgsConstructor
public class MyLeavePersistenceService {

    private final LeaveBalanceRepository balanceRepository;

    /**
     * 있으면 총 연차를 고치고 참을 준다. 없으면 아무것도 하지 않고 거짓 — 호출자가 생성으로 넘어간다.
     *
     * @return 고쳤으면 {@code true}
     */
    @Transactional
    public boolean updateTotalIfPresent(String guestId, double totalDays) {
        return balanceRepository.findByGuestId(guestId)
                .map(balance -> {
                    balance.changeTotal(totalDays);
                    balanceRepository.save(balance);
                    return true;
                })
                .orElse(false);
    }

    /**
     * 새로 만든다. 같은 소유자로 동시에 들어오면 한쪽이 {@code uk_leave_balance_guest} 에 걸려
     * {@link org.springframework.dao.DataIntegrityViolationException} 을 던진다 — <b>이 트랜잭션만</b> 롤백되고
     * 호출자가 그걸 잡아 갱신으로 되돌아간다.
     *
     * <p>{@code REQUIRES_NEW} 인 이유 — 호출자가 어쩌다 트랜잭션 안이어도 실패가 <b>그쪽으로 번지지 않게</b> 한다.
     * 같은 트랜잭션을 공유하면 제약 위반이 바깥을 rollback-only 로 찍어, 예외를 잡아도 커밋에서
     * {@code UnexpectedRollbackException} 이 난다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void create(String guestId, double totalDays) {
        balanceRepository.save(LeaveBalance.of(guestId, totalDays));
    }
}
