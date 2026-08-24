package com.offway.core.common.batch.repository;

import com.offway.core.common.batch.domain.BatchRun;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** port 구현(adapter) — Spring Data 에 위임. */
@Slf4j
@Repository
@RequiredArgsConstructor
public class BatchRunRepositoryImpl implements BatchRunRepository {

    private final BatchRunJpaRepository jpaRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean hasRunOn(String name, LocalDate date) {
        return jpaRepository.findByName(name).map(run -> run.ranOn(date)).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasRunSince(String name, LocalDateTime since) {
        return jpaRepository.findByName(name)
                .map(run -> !run.getLastRunAt().isBefore(since))
                .orElse(false);
    }

    @Override
    @Transactional
    public void markStarted(String name, LocalDateTime at) {
        jpaRepository
                .findByName(name)
                .ifPresentOrElse(
                        run -> run.markStartedAt(at),
                        () -> jpaRepository.save(BatchRun.startedAt(name, at)));
    }

    /**
     * <b>트랜잭션으로 감싸지 않는다.</b> 아래 INSERT 는 경합에서 실패하는 것이 정상 경로인데, 하나의 큰
     * 트랜잭션 안이면 그 실패가 트랜잭션 전체를 롤백 상태로 만들어 <b>이후 판단을 못 한다</b>. 세 호출이
     * 각자 자기 트랜잭션을 갖게 두고, 실패는 이 메서드가 값으로 처리한다.
     */
    @Override
    public boolean tryStartOn(String name, LocalDate date, LocalDateTime at) {
        if (jpaRepository.claimIfNotRunOn(name, at, date.atStartOfDay(), date.plusDays(1).atStartOfDay()) > 0) {
            return true;
        }
        // 갱신이 0건인 경우는 둘이다 — 이미 오늘 기록이 있거나(진 것), 행 자체가 아직 없거나.
        // 뒤쪽만 INSERT 로 내려가고, 그 경합은 uk_batch_run_name 이 갈라 한쪽만 통과시킨다.
        if (jpaRepository.findByName(name).isPresent()) {
            return false;
        }
        try {
            // saveAndFlush 가 아니라 save 다 — 바깥 트랜잭션이 없으므로 flush 를 부를 곳이 없고
            // ("No EntityManager with actual transaction available"), save 는 자기 트랜잭션 경계에서
            // 커밋하며 충돌을 드러낸다.
            jpaRepository.save(BatchRun.startedAt(name, at));
            return true;
        } catch (DataAccessException lostTheRace) {
            // 다른 실행이 먼저 만들었다는 뜻인데, 그것이 두 얼굴로 온다 — 유니크 위반(Duplicate entry)
            // 이거나 그 위반이 잠금 경합으로 나타난 것(InnoDB Deadlock)이다. 실측에서 후자가 나왔고,
            // DataIntegrityViolationException 만 잡으면 그 경우에 예외가 배치까지 올라간다.
            //
            // 다른 원인의 실패까지 여기서 "졌다" 로 접히지만, 그 방향이 안전하다 — 이번 회차를 건너뛸 뿐
            // 같은 날 외부 호출을 두 배로 쏘지는 않는다.
            log.info("배치 실행 선점 경합에서 밀렸습니다 name={} cause={}",
                    name, lostTheRace.getClass().getSimpleName());
            return false;
        }
    }
}
